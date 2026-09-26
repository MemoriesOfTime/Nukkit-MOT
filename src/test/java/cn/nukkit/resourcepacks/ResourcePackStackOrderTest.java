package cn.nukkit.resourcepacks;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.lang.BaseLang;
import cn.nukkit.resourcepacks.loader.ResourcePackLoader;
import cn.nukkit.resourcepacks.loader.ZippedResourcePackLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
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
 * 资源包栈顺序：packs.yml 的 priority 决定上下层，未设置时按加载顺序，且不依赖文件系统。
 * <p>
 * Stack order: the {@code priority} in packs.yml decides which pack is on top; without it the
 * load order applies, and neither depends on the order the filesystem lists the files in.
 */
class ResourcePackStackOrderTest {

    private static final String A = "aaaaaaaa-0000-0000-0000-000000000001";
    private static final String B = "bbbbbbbb-0000-0000-0000-000000000002";
    private static final String C = "cccccccc-0000-0000-0000-000000000003";

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

    private static void createPackZip(File dir, String name, String uuid) throws IOException {
        String manifest = "{\"format_version\":2,\"header\":{\"name\":\"" + name + "\",\"description\":\"d\","
                + "\"uuid\":\"" + uuid + "\",\"version\":[1,0,0],\"min_engine_version\":[1,21,0]},"
                + "\"modules\":[{\"type\":\"resources\","
                + "\"uuid\":\"" + UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)) + "\","
                + "\"version\":[1,0,0]}]}";
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(new File(dir, name + ".zip")))) {
            zos.putNextEntry(new ZipEntry("manifest.json"));
            zos.write(manifest.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }
}
