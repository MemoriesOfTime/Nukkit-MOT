package cn.nukkit.resourcepacks;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.lang.BaseLang;
import cn.nukkit.resourcepacks.loader.ZippedResourcePackLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Loader × snapshot cache: directory packs are zipped into the cache dir,
 * loadPacks wipes leftovers/orphans, and wiped instances keep serving.
 */
class ZippedResourcePackLoaderTest {

    private static final String MANIFEST = "{\"format_version\":2,\"header\":{\"name\":\"T\","
            + "\"description\":\"d\",\"uuid\":\"12345678-1234-1234-1234-123456789012\","
            + "\"version\":[1,0,0],\"min_engine_version\":[1,21,0]},\"modules\":[{\"type\":"
            + "\"resources\",\"uuid\":\"87654321-4321-4321-4321-210987654321\",\"version\":[1,0,0]}]}";

    @TempDir
    Path tempDir;

    private File packsDir;
    private File cacheRoot;

    @BeforeEach
    void setUp() throws Exception {
        MockServer.reset();
        BaseLang language = mock(BaseLang.class);
        when(language.translateString(any(String.class), any(Object[].class))).thenReturn("loaded");
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
        assertTrue(Files.isRegularFile(cacheDir.resolve("MyDirPack.zip")));
    }
}
