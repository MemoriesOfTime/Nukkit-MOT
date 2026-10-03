package cn.nukkit.resourcepacks;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.lang.BaseLang;
import cn.nukkit.resourcepacks.loader.ResourcePackLoader;
import cn.nukkit.resourcepacks.loader.ZippedBehaviourPackLoader;
import cn.nukkit.resourcepacks.loader.ZippedResourcePackLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * reloadPacks() 语义：close-before-load 顺序、重载不累积条目。
 * <p>
 * reloadPacks() semantics: close-before-load ordering and no entry
 * accumulation across reloads.
 */
class ResourcePackManagerReloadTest {

    private static final String VALID_UUID = "12345678-1234-1234-1234-123456789012";

    @TempDir
    Path tempDir;

    private File resourcePacksDir;
    private File behaviourPacksDir;

    @BeforeEach
    void setUp() throws Exception {
        MockServer.reset();
        BaseLang language = mock(BaseLang.class);
        when(language.translateString(any(String.class), any(Object[].class))).thenReturn("loaded");
        when(Server.getInstance().getLanguage()).thenReturn(language);
        resourcePacksDir = Files.createDirectories(tempDir.resolve("resource_packs")).toFile();
        behaviourPacksDir = Files.createDirectories(tempDir.resolve("behaviour_packs")).toFile();
        ZippedResourcePack.cacheRootOverride = tempDir.resolve("snapshot-cache").toFile();
    }

    @AfterEach
    void tearDown() {
        ZippedResourcePack.cacheRootOverride = null;
    }

    private void createPackZip(File dir, String name, String moduleType) throws Exception {
        String manifest = "{\"format_version\":2,\"header\":{\"name\":\"T\",\"description\":\"d\","
                + "\"uuid\":\"" + VALID_UUID + "\",\"version\":[1,0,0],\"min_engine_version\":[1,21,0]},"
                + "\"modules\":[{\"type\":\"" + moduleType + "\","
                + "\"uuid\":\"87654321-4321-4321-4321-210987654321\",\"version\":[1,0,0]}]}";
        File packFile = new File(dir, name + ".zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(packFile))) {
            zos.putNextEntry(new ZipEntry("manifest.json"));
            zos.write(manifest.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }

    private ResourcePackManager newManager(ResourcePackLoader... loaders) {
        return new ResourcePackManager(Set.of(loaders), tempDir.resolve("packs.yml").toFile());
    }

    @Test
    void reloadClosesOldInstancesBeforeLoadingNewOnes() throws Exception {
        File packFile = new File(resourcePacksDir, "reloadable.zip");
        createPackZip(resourcePacksDir, "reloadable", "resources");

        class OrderRecordingLoader implements ResourcePackLoader {
            ZippedResourcePack lastLoaded;

            @Override
            public List<ResourcePack> loadPacks() {
                if (this.lastLoaded != null && !this.lastLoaded.isSnapshotClosed()) {
                    throw new AssertionError("old pack instance must be closed before loaders run "
                            + "(Windows: a pinned snapshot name cannot be re-created)");
                }
                this.lastLoaded = new ZippedResourcePack(packFile);
                return List.of(this.lastLoaded);
            }
        }
        OrderRecordingLoader loader = new OrderRecordingLoader();
        ResourcePackManager manager = newManager(loader);
        ZippedResourcePack first = loader.lastLoaded;
        assertNotNull(first);
        assertFalse(first.isSnapshotClosed());

        manager.reloadPacks();

        assertTrue(first.isSnapshotClosed(), "old instance must be closed after reload");
        assertNotSame(first, loader.lastLoaded);
        assertFalse(loader.lastLoaded.isSnapshotClosed(), "new instance must keep serving");
        assertSame(loader.lastLoaded, manager.getPackById(first.getPackId()),
                "reload must re-register the new instance under the same pack id");
    }

    @Test
    void reloadClosesInstancesShadowedByDuplicatePackId() throws Exception {
        // 两个同 UUID 的包：allPacksById 只保留后加载者，resourcePacks set 按 equals
        // （packId）去重只保留先加载者 — 后者不在 map 里，漏关即泄漏快照 channel。
        // <p>
        // Two packs sharing a UUID: allPacksById keeps only the later one, while
        // the resourcePacks set (deduped by equals = packId) keeps only the
        // earlier one — the shadowed instance is absent from the map, so missing
        // it on close leaks the snapshot channel.
        createPackZip(resourcePacksDir, "dup-a", "resources");
        createPackZip(resourcePacksDir, "dup-b", "resources");
        ResourcePackManager manager = newManager(new ZippedResourcePackLoader(resourcePacksDir));

        // 与 listFiles 顺序无关：set 持有先加载者，map 持有后加载者，二者必为不同实例
        ZippedResourcePack fromStack = (ZippedResourcePack) manager.getResourceStack(GameVersion.getLastVersion())[0];
        ZippedResourcePack fromMap = (ZippedResourcePack) manager.getPackById(UUID.fromString(VALID_UUID));
        assertNotSame(fromStack, fromMap, "same-UUID packs must yield two distinct live instances");
        assertFalse(fromStack.isSnapshotClosed());
        assertFalse(fromMap.isSnapshotClosed());

        manager.reloadPacks();

        assertTrue(fromStack.isSnapshotClosed(), "shadowed instance (set-only) must be closed on reload");
        assertTrue(fromMap.isSnapshotClosed(), "winning instance (map) must be closed on reload");
    }

    @Test
    void reloadDoesNotAccumulatePackEntries() throws Exception {
        createPackZip(resourcePacksDir, "plain", "resources");
        createPackZip(behaviourPacksDir, "behaviour", "data");
        ResourcePackManager manager = newManager(
                new ZippedResourcePackLoader(resourcePacksDir),
                new ZippedBehaviourPackLoader(behaviourPacksDir));
        GameVersion version = GameVersion.getLastVersion();
        assertEquals(1, manager.getResourceStack(version).length);
        assertEquals(1, manager.getBehaviorStack(version).length);

        manager.reloadPacks();

        assertEquals(1, manager.getResourceStack(version).length,
                "resource pack entries must not accumulate across reloads");
        assertEquals(1, manager.getBehaviorStack(version).length,
                "behaviour pack entries must not accumulate across reloads");
    }
}
