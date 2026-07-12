package cn.nukkit.utils.serverconfig;

import cn.nukkit.resourcepacks.ResourcePack;
import cn.nukkit.resourcepacks.loader.ZippedResourcePackLoader;
import cn.nukkit.utils.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ResourcePackMigrationTest {

    private static final UUID RESOURCE_PACK_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BEHAVIOUR_PACK_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String ENCRYPTION_KEY = "0123456789abcdefGHIJKLMNOPQRSTUV";

    @TempDir
    Path tempDir;

    @Test
    void migrateResourcePackReadsUuidFromMovedPackAndStoresKey() throws IOException {
        Path legacyDir = Files.createDirectories(tempDir.resolve("resource_packs_netease"));
        Path pack = createPack(legacyDir.resolve("legacy.mcpack"), RESOURCE_PACK_ID, "resources");
        Path key = Files.writeString(legacyDir.resolve("legacy.mcpack.key"), ENCRYPTION_KEY, StandardCharsets.UTF_8);

        ResourcePackMigration.migrate(tempDir.toFile());

        assertTrue(Files.exists(tempDir.resolve("resource_packs/legacy.netease.mcpack")));
        assertFalse(Files.exists(pack));
        assertFalse(Files.exists(key));
        Config config = new Config(tempDir.resolve("resource_packs/packs.yml").toFile(), Config.YAML);
        assertEquals(ENCRYPTION_KEY, config.getString(RESOURCE_PACK_ID + ".key"));
        assertFalse(Files.exists(tempDir.resolve("resource_packs/legacy.mcpack.netease.key")));
    }

    @Test
    void migrateBehaviourPackStoresKeyInSharedPackConfig() throws IOException {
        Path legacyDir = Files.createDirectories(tempDir.resolve("behaviour_packs_netease"));
        createPack(legacyDir.resolve("behaviour.mcpack"), BEHAVIOUR_PACK_ID, "data");
        Files.writeString(legacyDir.resolve("behaviour.mcpack.key"), ENCRYPTION_KEY, StandardCharsets.UTF_8);

        ResourcePackMigration.migrate(tempDir.toFile());

        Config config = new Config(tempDir.resolve("resource_packs/packs.yml").toFile(), Config.YAML);
        assertEquals(ENCRYPTION_KEY, config.getString(BEHAVIOUR_PACK_ID + ".key"));
        assertFalse(Files.exists(tempDir.resolve("behaviour_packs/packs.yml")));
    }

    @Test
    void migratePackWithoutReadableUuidKeepsKeyBesideMigratedPack() throws IOException {
        Path legacyDir = Files.createDirectories(tempDir.resolve("resource_packs_netease"));
        Path pack = legacyDir.resolve("broken.mcpack");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(pack))) {
            zip.putNextEntry(new ZipEntry("readme.txt"));
            zip.write("missing manifest".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        Files.writeString(legacyDir.resolve("broken.mcpack.key"), ENCRYPTION_KEY, StandardCharsets.UTF_8);

        ResourcePackMigration.migrate(tempDir.toFile());

        Path migratedKey = tempDir.resolve("resource_packs/broken.netease.mcpack.key");
        assertTrue(Files.exists(migratedKey));
        assertEquals(ENCRYPTION_KEY, Files.readString(migratedKey, StandardCharsets.UTF_8));
        assertFalse(Files.exists(tempDir.resolve("resource_packs/broken.mcpack.netease.key")));
    }

    @Test
    void fallbackMoveFailureKeepsSourceKeyForRecovery() throws IOException {
        Path legacyDir = Files.createDirectories(tempDir.resolve("resource_packs_netease"));
        Path pack = legacyDir.resolve("blocked.mcpack");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(pack))) {
            zip.putNextEntry(new ZipEntry("readme.txt"));
            zip.write("missing manifest".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        Path sourceKey = Files.writeString(legacyDir.resolve("blocked.mcpack.key"), ENCRYPTION_KEY, StandardCharsets.UTF_8);
        Path destinationDir = Files.createDirectories(tempDir.resolve("resource_packs"));
        Path destinationKey = Files.writeString(
                destinationDir.resolve("blocked.netease.mcpack.key"),
                "existing-key",
                StandardCharsets.UTF_8
        );

        ResourcePackMigration.migrate(tempDir.toFile());

        assertTrue(Files.exists(sourceKey));
        assertEquals(ENCRYPTION_KEY, Files.readString(sourceKey, StandardCharsets.UTF_8));
        assertEquals("existing-key", Files.readString(destinationKey, StandardCharsets.UTF_8));
    }

    @Test
    void orphanedKeyFromInterruptedMigrationIsPreserved() throws IOException {
        Path legacyDir = Files.createDirectories(tempDir.resolve("resource_packs_netease"));
        Path sourceKey = Files.writeString(
                legacyDir.resolve("interrupted.mcpack.key"),
                ENCRYPTION_KEY,
                StandardCharsets.UTF_8
        );
        Path destinationDir = Files.createDirectories(tempDir.resolve("resource_packs"));
        createPack(destinationDir.resolve("interrupted.netease.mcpack"), RESOURCE_PACK_ID, "resources");

        ResourcePackMigration.migrate(tempDir.toFile());

        assertTrue(Files.exists(sourceKey));
        assertEquals(ENCRYPTION_KEY, Files.readString(sourceKey, StandardCharsets.UTF_8));
    }

    @Test
    void migrateNameContainingNeteaseTextStillAddsExactSuffix() throws IOException {
        Path legacyDir = Files.createDirectories(tempDir.resolve("resource_packs_netease"));
        createPack(legacyDir.resolve("my.netease-tools.mcpack"), RESOURCE_PACK_ID, "resources");

        ResourcePackMigration.migrate(tempDir.toFile());

        assertTrue(Files.exists(tempDir.resolve("resource_packs/my.netease-tools.netease.mcpack")));
        assertFalse(Files.exists(tempDir.resolve("resource_packs/my.netease-tools.mcpack")));
    }

    @Test
    void detectSupportTypeRecognizesMigratedDirectorySuffix() throws IOException {
        ExposedResourcePackLoader loader = new ExposedResourcePackLoader(Files.createDirectory(tempDir.resolve("packs")));

        assertEquals(ResourcePack.SupportType.NETEASE, loader.detect("directory.netease"));
        assertEquals(ResourcePack.SupportType.NETEASE, loader.detect("archive.netease.mcpack"));
        assertEquals(ResourcePack.SupportType.UNIVERSAL, loader.detect("archive.mcpack"));
    }

    @Test
    void resourcePackLoaderIgnoresPackConfigurationFile() throws IOException {
        ExposedResourcePackLoader loader = new ExposedResourcePackLoader(Files.createDirectory(tempDir.resolve("configured-packs")));

        assertTrue(loader.ignore("packs.yml"));
        assertTrue(loader.ignore("archive.mcpack.key"));
        assertFalse(loader.ignore("archive.mcpack"));
    }

    private static Path createPack(Path path, UUID uuid, String moduleType) throws IOException {
        String manifest = """
                {
                  "format_version": 2,
                  "header": {
                    "name": "Migration Test Pack",
                    "description": "Migration test fixture",
                    "uuid": "%s",
                    "version": [1, 0, 0]
                  },
                  "modules": [
                    {
                      "type": "%s",
                      "uuid": "33333333-3333-3333-3333-333333333333",
                      "version": [1, 0, 0]
                    }
                  ]
                }
                """.formatted(uuid, moduleType);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(manifest.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return path;
    }

    private static final class ExposedResourcePackLoader extends ZippedResourcePackLoader {

        private ExposedResourcePackLoader(Path path) {
            super(path.toFile());
        }

        private ResourcePack.SupportType detect(String fileName) {
            return detectSupportType(fileName);
        }

        private boolean ignore(String fileName) {
            return shouldIgnoreFile(fileName);
        }
    }
}
