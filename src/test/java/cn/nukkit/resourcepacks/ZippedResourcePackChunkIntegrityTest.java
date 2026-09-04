package cn.nukkit.resourcepacks;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.lang.BaseLang;
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
}
