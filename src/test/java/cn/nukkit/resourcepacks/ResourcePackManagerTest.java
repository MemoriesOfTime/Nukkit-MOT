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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ResourcePackManagerTest {

    @Nested
    class PackConfigParsing {

        @TempDir
        Path tempDir;

        @BeforeEach
        void setUp() {
            MockServer.reset();
            BaseLang language = mock(BaseLang.class);
            when(language.translateString(any(String.class), any(String[].class))).thenReturn("loaded");
            when(Server.getInstance().getLanguage()).thenReturn(language);
        }

        @Test
        void invalidPackConfigDoesNotAbortReload() throws IOException {
            Path packConfig = Files.writeString(
                    tempDir.resolve("packs.yml"),
                    "invalid: [unterminated",
                    StandardCharsets.UTF_8
            );

            assertDoesNotThrow(() -> new ResourcePackManager(Set.of(() -> java.util.List.of()), packConfig.toFile()));
        }

        @Test
        void invalidPackConfigStructureDoesNotAbortReload() throws IOException {
            UUID packId = UUID.fromString("11111111-1111-1111-1111-111111111111");
            ResourcePack pack = mock(ResourcePack.class);
            when(pack.getPackId()).thenReturn(packId);
            when(pack.isBehaviourPack()).thenReturn(false);
            Path packConfig = Files.writeString(
                    tempDir.resolve("packs.yml"),
                    packId + ":\n  cdn: https://example.invalid/pack.mcpack\nnot-a-pack: scalar\n",
                    StandardCharsets.UTF_8
            );

            assertDoesNotThrow(() -> new ResourcePackManager(Set.of(() -> List.of(pack)), packConfig.toFile()));
            verify(pack, never()).setCDNUrl(anyString());
        }

        @Test
        void validPackConfigIsStillApplied() throws IOException {
            UUID packId = UUID.fromString("22222222-2222-2222-2222-222222222222");
            ResourcePack pack = mock(ResourcePack.class);
            when(pack.getPackId()).thenReturn(packId);
            when(pack.isBehaviourPack()).thenReturn(false);
            Path packConfig = Files.writeString(
                    tempDir.resolve("packs.yml"),
                    packId + ":\n  cdn: https://example.invalid/pack.mcpack\n  key: test-key\n",
                    StandardCharsets.UTF_8
            );

            new ResourcePackManager(Set.of(() -> List.of(pack)), packConfig.toFile());

            verify(pack).setCDNUrl("https://example.invalid/pack.mcpack");
            verify(pack).setEncryptionKey("test-key");
        }

        @Test
        void emptyConfiguredKeyDisablesPackEncryption() throws IOException {
            UUID packId = UUID.fromString("33333333-3333-3333-3333-333333333333");
            ResourcePack pack = mock(ResourcePack.class);
            when(pack.getPackId()).thenReturn(packId);
            when(pack.isBehaviourPack()).thenReturn(false);
            Path packConfig = Files.writeString(
                    tempDir.resolve("packs.yml"),
                    packId + ":\n  key: \"\"\n",
                    StandardCharsets.UTF_8
            );

            new ResourcePackManager(Set.of(() -> List.of(pack)), packConfig.toFile());

            verify(pack).setEncryptionKey("");
        }
    }

    /**
     * reloadPacks() 语义：close-before-load 顺序、重载不累积条目。
     * <p>
     * reloadPacks() semantics: close-before-load ordering and no entry
     * accumulation across reloads.
     */
    @Nested
    class ReloadSemantics {

        private static final String VALID_UUID = "12345678-1234-1234-1234-123456789012";

        @TempDir
        Path tempDir;

        private File resourcePacksDir;
        private File behaviourPacksDir;

        @BeforeEach
        void setUp() throws Exception {
            MockServer.reset();
            BaseLang language = mock(BaseLang.class);
            when(language.translateString(any(String.class), any(String[].class))).thenReturn("loaded");
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

    /**
     * 资源包栈顺序：packs.yml 的 priority 决定上下层，未设置时按加载顺序，且不依赖文件系统。
     * <p>
     * Stack order: the {@code priority} in packs.yml decides which pack is on top; without it the
     * load order applies, and neither depends on the order the filesystem lists the files in.
     */
    @Nested
    class StackOrder {

        private static final String A = "aaaaaaaa-0000-0000-0000-000000000001";
        private static final String B = "bbbbbbbb-0000-0000-0000-000000000002";
        private static final String C = "cccccccc-0000-0000-0000-000000000003";

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
        void packsWithoutPriorityKeepTheirLoadOrder() {
            ResourcePackManager manager = manager("", pack(A, false), pack(B, false), pack(C, false));

            assertEquals(List.of(A, B, C), ids(manager.getResourceStack(GameVersion.getLastVersion())));
        }

        @Test
        void aHigherPriorityPlacesAPackHigherInTheStack() {
            ResourcePackManager manager = manager(
                    C + ":\n  priority: 10\n" + A + ":\n  priority: -1\n",
                    pack(A, false), pack(B, false), pack(C, false));

            assertEquals(List.of(C, B, A), ids(manager.getResourceStack(GameVersion.getLastVersion())),
                    "10 on top, the undeclared pack at the default 0 in the middle, -1 at the bottom");
        }

        @Test
        void equalPrioritiesKeepTheirLoadOrder() {
            ResourcePackManager manager = manager(
                    A + ":\n  priority: 5\n" + C + ":\n  priority: 5\n",
                    pack(A, false), pack(B, false), pack(C, false));

            assertEquals(List.of(A, C, B), ids(manager.getResourceStack(GameVersion.getLastVersion())));
        }

        @Test
        void anInvalidPriorityIsIgnoredAndTheReloadGoesOn() {
            ResourcePack b = pack(B, false);
            ResourcePackManager manager = manager(
                    B + ":\n  priority: high\n  cdn: https://example.invalid/b.mcpack\n",
                    pack(A, false), b, pack(C, false));

            assertEquals(List.of(A, B, C), ids(manager.getResourceStack(GameVersion.getLastVersion())));
            org.mockito.Mockito.verify(b).setCDNUrl("https://example.invalid/b.mcpack");
        }

        @Test
        void behaviourPacksAreOrderedByTheirOwnPriority() {
            ResourcePackManager manager = manager(
                    B + ":\n  priority: 3\n",
                    pack(A, true), pack(B, true), pack(C, false));

            assertEquals(List.of(B, A), ids(manager.getBehaviorStack(GameVersion.getLastVersion())));
            assertEquals(List.of(C), ids(manager.getResourceStack(GameVersion.getLastVersion())));
        }

        @Test
        void loadersKeepTheirRegistrationOrder() {
            List<ResourcePackLoader> loaders = new ArrayList<>();
            List<String> expected = new ArrayList<>();
            for (int i = 0; i < 16; i++) {
                String id = String.format("dddddddd-0000-0000-0000-%012d", i);
                ResourcePack pack = pack(id, false);
                loaders.add(() -> List.of(pack));
                expected.add(id);
            }

            ResourcePackManager manager = new ResourcePackManager(loaders.toArray(new ResourcePackLoader[0]));

            assertEquals(expected, ids(manager.getResourceStack(GameVersion.getLastVersion())));
        }

        private ResourcePackManager manager(String packsYml, ResourcePack... packs) {
            File config = this.tempDir.resolve("packs.yml").toFile();
            try {
                Files.writeString(config.toPath(), packsYml.isEmpty() ? "# no pack settings\n" : packsYml,
                        StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            List<ResourcePack> loaded = Arrays.asList(packs);
            return new ResourcePackManager(Set.of(() -> loaded), config);
        }

        private static ResourcePack pack(String id, boolean behaviour) {
            ResourcePack pack = mock(ResourcePack.class);
            when(pack.getPackId()).thenReturn(UUID.fromString(id));
            when(pack.isBehaviourPack()).thenReturn(behaviour);
            when(pack.getSupportType()).thenReturn(ResourcePack.SupportType.UNIVERSAL);
            return pack;
        }

        private static List<String> ids(ResourcePack[] stack) {
            return Arrays.stream(stack).map(pack -> pack.getPackId().toString()).toList();
        }
    }
}
