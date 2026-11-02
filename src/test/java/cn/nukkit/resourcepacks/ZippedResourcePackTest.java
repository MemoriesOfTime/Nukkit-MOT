package cn.nukkit.resourcepacks;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.lang.BaseLang;
import cn.nukkit.resourcepacks.loader.ResourcePackLoader;
import cn.nukkit.resourcepacks.loader.ZippedResourcePackLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ZippedResourcePackTest {

    /**
     * Loader × snapshot cache: directory packs are zipped into the cache dir,
     * loadPacks wipes leftovers/orphans, and wiped instances keep serving.
     */
    @Nested
    class LoaderSnapshotCache {

        private static final String MANIFEST = "{\"format_version\":2,\"header\":{\"name\":\"T\","
                + "\"description\":\"d\",\"uuid\":\"12345678-1234-1234-1234-123456789012\","
                + "\"version\":[1,0,0],\"min_engine_version\":[1,21,0]},\"modules\":[{\"type\":"
                + "\"resources\",\"uuid\":\"87654321-4321-4321-4321-210987654321\",\"version\":[1,0,0]}]}";

        private static final String A = "aaaaaaaa-0000-0000-0000-000000000001";
        private static final String B = "bbbbbbbb-0000-0000-0000-000000000002";
        private static final String C = "cccccccc-0000-0000-0000-000000000003";

        @TempDir
        Path tempDir;

        private File packsDir;
        private File cacheRoot;

        @BeforeEach
        void setUp() throws Exception {
            MockServer.reset();
            BaseLang language = mock(BaseLang.class);
            when(language.translateString(any(String.class), any(String[].class))).thenReturn("loaded");
            when(Server.getInstance().getLanguage()).thenReturn(language);

            packsDir = Files.createDirectories(tempDir.resolve("resource_packs")).toFile();
            cacheRoot = tempDir.resolve("snapshot-cache").toFile();
            ZippedResourcePack.cacheRootOverride = cacheRoot;
        }

        @AfterEach
        void tearDown() {
            ZippedResourcePack.cacheRootOverride = null;
        }

        private void createDirectoryPack(String name) throws Exception {
            Path dir = Files.createDirectories(packsDir.toPath().resolve(name));
            Files.writeString(dir.resolve("manifest.json"), MANIFEST, StandardCharsets.UTF_8);
            Files.createDirectories(dir.resolve("textures"));
            Files.write(dir.resolve("textures/blob.bin"), new byte[128 * 1024]);
        }

        @Test
        void directoryPackIsZippedIntoSnapshotCacheAndSurvivesSourceRemoval() throws Exception {
            createDirectoryPack("MyDirPack");
            ZippedResourcePackLoader loader = new ZippedResourcePackLoader(packsDir);

            List<ResourcePack> packs = loader.loadPacks();

            assertEquals(1, packs.size());
            ZippedResourcePack pack = (ZippedResourcePack) packs.get(0);
            assertEquals("1.0.0", pack.getPackVersion());

            // Directory pack zip must live in the snapshot cache, not system temp
            Path snapshot = cacheRoot.toPath().resolve("resource_packs/MyDirPack.zip");
            assertTrue(Files.isRegularFile(snapshot), "directory pack zip should live in snapshot cache");

            // Serving survives source removal (channel pinned to the snapshot inode)
            try (var walk = Files.walk(packsDir.toPath().resolve("MyDirPack"))) {
                for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.delete(p);
                }
            }
            java.io.ByteArrayOutputStream reassembled = new java.io.ByteArrayOutputStream();
            for (int off = 0; off < pack.getPackSize(); off += 100 * 1024) {
                reassembled.write(pack.getPackChunk(off, 100 * 1024));
            }
            assertEquals(pack.getPackSize(), reassembled.size());
            assertArrayEquals(pack.getSha256(),
                    MessageDigest.getInstance("SHA-256").digest(reassembled.toByteArray()));
        }

        @Test
        void loadPacksWipesOrphanedAndCrashLeftoverCacheEntries() throws Exception {
            createDirectoryPack("MyDirPack");
            ZippedResourcePackLoader loader = new ZippedResourcePackLoader(packsDir);
            List<ResourcePack> first = loader.loadPacks();
            assertEquals(1, first.size());

            // Simulate crash leftovers (.tmp) and an orphan of a removed pack
            Path cacheDir = cacheRoot.toPath().resolve("resource_packs");
            Files.write(cacheDir.resolve("crash-leftover.zip.abc123.tmp"), new byte[] {1});
            Files.write(cacheDir.resolve("removed-pack.zip"), new byte[] {2});

            List<ResourcePack> second = loader.loadPacks();

            assertEquals(1, second.size());
            assertFalse(Files.exists(cacheDir.resolve("crash-leftover.zip.abc123.tmp")),
                    "crash leftover should be wiped");
            assertFalse(Files.exists(cacheDir.resolve("removed-pack.zip")),
                    "orphaned snapshot should be wiped");
            // Windows 降级路径：首个实例的 channel 仍钉住 stable 名（delete-pending）时
            // promoteSnapshot 失败，快照保留 .tmp 名 — 两种名字都算加载成功。
            // Windows degradation: while the first instance's channel still pins the
            // stable name, promotion fails and the snapshot keeps its .tmp name.
            try (var snapshots = Files.list(cacheDir)) {
                assertTrue(snapshots.filter(Files::isRegularFile).anyMatch(p -> {
                            String name = p.getFileName().toString();
                            return name.equals("MyDirPack.zip")
                                    || (name.startsWith("MyDirPack.zip.") && name.endsWith(".tmp"));
                        }),
                        "directory pack snapshot should exist (stable or tmp-fallback name)");
            }
        }

        @Test
        void theLoaderReadsPackFilesByName() throws IOException {
            File dir = Files.createDirectories(this.tempDir.resolve("resource_packs")).toFile();
            // Written in reverse order: the stack must not follow creation or directory order.
            createPackZip(dir, "c-pack", C);
            createPackZip(dir, "b-pack", B);
            createPackZip(dir, "a-pack", A);

            List<ResourcePack> packs = new ZippedResourcePackLoader(dir).loadPacks();
            try {
                assertEquals(List.of(A, B, C), ids(packs.toArray(ResourcePack[]::new)));
            } finally {
                for (ResourcePack pack : packs) {
                    if (pack instanceof AutoCloseable closeable) {
                        try {
                            closeable.close();
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }

        @Test
        void listingAMissingDirectoryIsEmpty() {
            assertEquals(0, ResourcePackLoader.listFilesInNameOrder(this.tempDir.resolve("absent").toFile()).length);
        }

        private static List<String> ids(ResourcePack[] stack) {
            return Arrays.stream(stack).map(pack -> pack.getPackId().toString()).toList();
        }

        private static void createPackZip(File dir, String name, String uuid) throws IOException {
            String manifest = "{\"format_version\":2,\"header\":{\"name\":\"" + name + "\",\"description\":\"d\","
                    + "\"uuid\":\"" + uuid + "\",\"version\":[1,0,0],\"min_engine_version\":[1,21,0]},"
                    + "\"modules\":[{\"type\":\"resources\","
                    + "\"uuid\":\"" + java.util.UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)) + "\","
                    + "\"version\":[1,0,0]}]}";
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(new File(dir, name + ".zip")))) {
                zos.putNextEntry(new ZipEntry("manifest.json"));
                zos.write(manifest.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
    }

    /**
     * 验证 ZippedResourcePack 的 getPackSize / getSha256 / getPackChunk 三者一致性。
     * <p>
     * 模拟客户端按 chunk 拉取后重新拼装，校验 SHA-256 必须与 getSha256 一致，
     * 否则真实客户端会报"资源包加载失败"。
     */
    @Nested
    class ChunkIntegrity {

        @TempDir
        Path tempDir;

        @BeforeEach
        void setUp() {
            MockServer.reset();
            BaseLang language = mock(BaseLang.class);
            when(language.translateString(any(String.class), any(String[].class))).thenReturn("loaded");
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
}
