package cn.nukkit.resourcepacks;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.lang.BaseLang;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证 ZippedResourcePack 的 getPackSize / getSha256 / getPackChunk 三者一致性。
 * <p>
 * 模拟客户端按 chunk 拉取后重新拼装，校验 SHA-256 必须与 getSha256 一致，
 * 否则真实客户端会报"资源包加载失败"。
 */
class ZippedResourcePackChunkIntegrityTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockServer.reset();
        BaseLang language = mock(BaseLang.class);
        when(language.translateString(any(String.class), any(Object[].class))).thenReturn("loaded");
        when(Server.getInstance().getLanguage()).thenReturn(language);
        ZippedResourcePack.cacheRootOverride = tempDir.resolve("snapshot-cache").toFile();
    }

    @AfterEach
    void tearDown() {
        ZippedResourcePack.cacheRootOverride = null;
    }

    @Test
    void chunkReassemblyMatchesSha256AndSize() throws Exception {
        String manifest = "{\"format_version\":2,\"header\":{\"name\":\"T\",\"description\":\"d\","
                + "\"uuid\":\"12345678-1234-1234-1234-123456789012\",\"version\":[1,0,0],"
                + "\"min_engine_version\":[1,21,0]},\"modules\":[{\"type\":\"resources\","
                + "\"uuid\":\"87654321-4321-4321-4321-210987654321\",\"version\":[1,0,0]}]}";

        File packFile = tempDir.resolve("pack.zip").toFile();
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("manifest.json", manifest.getBytes(StandardCharsets.UTF_8));
        // 300KB blob -> 3 chunks of 100KB（最后一块不满），覆盖非整除边界
        byte[] blob = new byte[300 * 1024];
        for (int i = 0; i < blob.length; i++) {
            blob[i] = (byte) (i % 251);
        }
        entries.put("data/blob.bin", blob);

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(packFile))) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }

        byte[] expectedSha = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(packFile.toPath()));
        int expectedSize = (int) packFile.length();

        ZippedResourcePack pack = new ZippedResourcePack(packFile);

        assertEquals(expectedSize, pack.getPackSize());
        assertArrayEquals(expectedSha, pack.getSha256());

        int chunkSize = 100 * 1024;
        int chunkCount = (expectedSize + chunkSize - 1) / chunkSize;
        java.io.ByteArrayOutputStream reassembled = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < chunkCount; i++) {
            byte[] chunk = pack.getPackChunk(i * chunkSize, chunkSize);
            assertNotNull(chunk);
            assertTrue(chunk.length > 0, "chunk " + i + " empty");
            assertTrue(chunk.length <= chunkSize, "chunk " + i + " oversized");
            reassembled.write(chunk);
        }

        byte[] reassembledBytes = reassembled.toByteArray();
        assertEquals(expectedSize, reassembledBytes.length, "reassembled length mismatch");
        assertArrayEquals(expectedSha, MessageDigest.getInstance("SHA-256").digest(reassembledBytes),
                "reassembled SHA-256 mismatch (client would reject this pack)");
    }

    @Test
    void replacementAfterLoadKeepsOneConsistentArchive() throws Exception {
        String manifest = "{\"format_version\":2,\"header\":{\"name\":\"T\",\"description\":\"d\","
                + "\"uuid\":\"12345678-1234-1234-1234-123456789012\",\"version\":[1,0,0],"
                + "\"min_engine_version\":[1,21,0]},\"modules\":[{\"type\":\"resources\","
                + "\"uuid\":\"87654321-4321-4321-4321-210987654321\",\"version\":[1,0,0]}]}";
        File packFile = tempDir.resolve("replaceable.zip").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(packFile))) {
            zos.putNextEntry(new ZipEntry("manifest.json"));
            zos.write(manifest.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("textures/icon.png"));
            zos.write(new byte[] {1, 2, 3, 4});
            zos.closeEntry();
        }

        byte[] expectedArchive = Files.readAllBytes(packFile.toPath());
        byte[] expectedSha = MessageDigest.getInstance("SHA-256").digest(expectedArchive);
        ZippedResourcePack pack = new ZippedResourcePack(packFile);

        Files.writeString(packFile.toPath(), "a different archive was deployed while the server stayed online");

        assertEquals(expectedArchive.length, pack.getPackSize());
        assertEquals("1.0.0", pack.getPackVersion());
        assertArrayEquals(expectedSha, pack.getSha256());
        java.io.ByteArrayOutputStream reassembled = new java.io.ByteArrayOutputStream();
        int chunkSize = 17;
        for (int off = 0; off < pack.getPackSize(); off += chunkSize) {
            reassembled.write(pack.getPackChunk(off, chunkSize));
        }
        assertArrayEquals(expectedArchive, reassembled.toByteArray(),
                "a running server must not mix a startup manifest with replacement bytes");
    }

    @Test
    void sourceRemovedAfterLoadStillServesSnapshotFromCache() throws Exception {
        String manifest = "{\"format_version\":2,\"header\":{\"name\":\"T\",\"description\":\"d\","
                + "\"uuid\":\"12345678-1234-1234-1234-123456789012\",\"version\":[1,0,0],"
                + "\"min_engine_version\":[1,21,0]},\"modules\":[{\"type\":\"resources\","
                + "\"uuid\":\"87654321-4321-4321-4321-210987654321\",\"version\":[1,0,0]}]}";
        File packFile = tempDir.resolve("removable.zip").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(packFile))) {
            zos.putNextEntry(new ZipEntry("manifest.json"));
            zos.write(manifest.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("data/blob.bin"));
            zos.write(new byte[150 * 1024]);
            zos.closeEntry();
        }
        byte[] expectedArchive = Files.readAllBytes(packFile.toPath());

        ZippedResourcePack pack = new ZippedResourcePack(packFile);

        // Source deleted at runtime (deployment cleanup); serving must not be affected
        Files.delete(packFile.toPath());

        assertEquals(expectedArchive.length, pack.getPackSize());
        java.io.ByteArrayOutputStream reassembled = new java.io.ByteArrayOutputStream();
        for (int off = 0; off < pack.getPackSize(); off += 100 * 1024) {
            reassembled.write(pack.getPackChunk(off, 100 * 1024));
        }
        assertArrayEquals(expectedArchive, reassembled.toByteArray());

        // The snapshot copy must live under the injected cache root, not DATA_PATH
        try (var snapshots = Files.walk(tempDir.resolve("snapshot-cache"))) {
            assertTrue(snapshots.filter(Files::isRegularFile)
                            .anyMatch(p -> p.getFileName().toString().startsWith("removable.zip")),
                    "snapshot copy should exist under the cache root");
        }
    }

    @Test
    void malformedManifestsAreRejectedWithoutLeakingSnapshot() throws Exception {
        // 每个变体此前都能通过旧的 verifyManifest（只查字段存在性），随后在构造成功
        // 之后的解析点抛异常（ZippedBehaviourPack 的 modules 扫描、getPackId）——那条
        // 路径没有清理入口，会泄漏已打开的快照 channel。现在必须在 verify 阶段拒绝，
        // 且失败清理不得留下快照文件。
        String[] malformedManifests = {
                // "modules" present but not an array
                "{\"format_version\":2,\"header\":{\"name\":\"T\",\"description\":\"d\","
                        + "\"uuid\":\"12345678-1234-1234-1234-123456789012\",\"version\":[1,0,0]},"
                        + "\"modules\":\"oops\"}",
                // header present but not an object
                "{\"format_version\":2,\"header\":\"oops\",\"modules\":[]}",
                // uuid present but unparseable (getPackId would throw later in the manager)
                "{\"format_version\":2,\"header\":{\"name\":\"T\",\"description\":\"d\","
                        + "\"uuid\":\"not-a-uuid\",\"version\":[1,0,0]},\"modules\":[]}",
                // uuid not a string at all
                "{\"format_version\":2,\"header\":{\"name\":\"T\",\"description\":\"d\","
                        + "\"uuid\":12345,\"version\":[1,0,0]},\"modules\":[]}",
                // version present but not a 3-element array
                "{\"format_version\":2,\"header\":{\"name\":\"T\",\"description\":\"d\","
                        + "\"uuid\":\"12345678-1234-1234-1234-123456789012\",\"version\":\"1.0.0\"},"
                        + "\"modules\":[]}",
                // min_engine_version not an array (getPackProtocol would throw IllegalStateException
                // on the login path — after construction succeeded)
                "{\"format_version\":2,\"header\":{\"name\":\"T\",\"description\":\"d\","
                        + "\"uuid\":\"12345678-1234-1234-1234-123456789012\",\"version\":[1,0,0],"
                        + "\"min_engine_version\":\"1.21.0\"},\"modules\":[]}",
                // min_engine_version with a non-numeric element (ProtocolConverter.getAsInt
                // would throw NumberFormatException)
                "{\"format_version\":2,\"header\":{\"name\":\"T\",\"description\":\"d\","
                        + "\"uuid\":\"12345678-1234-1234-1234-123456789012\",\"version\":[1,0,0],"
                        + "\"min_engine_version\":[true,0,0]},\"modules\":[]}",
                // min_engine_version too short (convertToProtocol requires >= 3 elements)
                "{\"format_version\":2,\"header\":{\"name\":\"T\",\"description\":\"d\","
                        + "\"uuid\":\"12345678-1234-1234-1234-123456789012\",\"version\":[1,0,0],"
                        + "\"min_engine_version\":[1,21]},\"modules\":[]}",
        };
        for (String manifest : malformedManifests) {
            File packFile = tempDir.resolve("malformed.zip").toFile();
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(packFile))) {
                zos.putNextEntry(new ZipEntry("manifest.json"));
                zos.write(manifest.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }

            assertThrows(IllegalArgumentException.class,
                    () -> new ZippedBehaviourPack(packFile, ResourcePack.SupportType.UNIVERSAL),
                    "manifest must be rejected at verify time: " + manifest);

            try (var leftovers = Files.walk(tempDir.resolve("snapshot-cache"))) {
                assertFalse(leftovers.filter(Files::isRegularFile).findAny().isPresent(),
                        "rejected manifest must not leave a snapshot file behind: " + manifest);
            }
        }
    }
}
