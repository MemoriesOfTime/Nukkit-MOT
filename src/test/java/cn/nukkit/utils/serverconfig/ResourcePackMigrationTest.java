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
    void migrateKeyPreservesExistingPackConfigWithoutTemporaryFiles() throws IOException {
        UUID configuredPackId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        Path legacyDir = Files.createDirectories(tempDir.resolve("resource_packs_netease"));
        createPack(legacyDir.resolve("legacy.mcpack"), RESOURCE_PACK_ID, "resources");
        Files.writeString(legacyDir.resolve("legacy.mcpack.key"), ENCRYPTION_KEY, StandardCharsets.UTF_8);
        Path destinationDir = Files.createDirectories(tempDir.resolve("resource_packs"));
        Files.writeString(
                destinationDir.resolve("packs.yml"),
                configuredPackId + ":\n  cdn: https://example.invalid/existing.mcpack\n",
                StandardCharsets.UTF_8
        );

        ResourcePackMigration.migrate(tempDir.toFile());

        Config config = new Config(destinationDir.resolve("packs.yml").toFile(), Config.YAML);
        assertEquals(
                "https://example.invalid/existing.mcpack",
                config.getString(configuredPackId + ".cdn")
        );
        assertEquals(ENCRYPTION_KEY, config.getString(RESOURCE_PACK_ID + ".key"));
        try (var files = Files.list(destinationDir)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().startsWith("packs.yml.")
                    && path.getFileName().toString().endsWith(".tmp")));
        }
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
    void migrateDirectoryPackStoresKeyInSharedPackConfig() throws IOException {
        Path legacyDir = Files.createDirectories(tempDir.resolve("resource_packs_netease"));
        Path packDirectory = Files.createDirectory(legacyDir.resolve("directory-pack"));
        Files.writeString(
                packDirectory.resolve("manifest.json"),
                manifestJson(RESOURCE_PACK_ID.toString(), "resources"),
                StandardCharsets.UTF_8
        );
        Path sourceKey = Files.writeString(
                legacyDir.resolve("directory-pack.key"),
                ENCRYPTION_KEY,
                StandardCharsets.UTF_8
        );

        ResourcePackMigration.migrate(tempDir.toFile());

        assertTrue(Files.isDirectory(tempDir.resolve("resource_packs/directory-pack.netease")));
        assertFalse(Files.exists(sourceKey));
        assertFalse(Files.exists(tempDir.resolve("resource_packs/directory-pack.netease.key")));
        Config config = new Config(tempDir.resolve("resource_packs/packs.yml").toFile(), Config.YAML);
        assertEquals(ENCRYPTION_KEY, config.getString(RESOURCE_PACK_ID + ".key"));
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
    void orphanedKeyFromInterruptedMigrationIsRecovered() throws IOException {
        Path legacyDir = Files.createDirectories(tempDir.resolve("resource_packs_netease"));
        Path sourceKey = Files.writeString(
                legacyDir.resolve("interrupted.mcpack.key"),
                ENCRYPTION_KEY,
                StandardCharsets.UTF_8
        );
        Path destinationDir = Files.createDirectories(tempDir.resolve("resource_packs"));
        createPack(destinationDir.resolve("interrupted.netease.mcpack"), RESOURCE_PACK_ID, "resources");

        ResourcePackMigration.migrate(tempDir.toFile());

        assertFalse(Files.exists(sourceKey));
        Config config = new Config(tempDir.resolve("resource_packs/packs.yml").toFile(), Config.YAML);
        assertEquals(ENCRYPTION_KEY, config.getString(RESOURCE_PACK_ID + ".key"));
    }

    @Test
    void destinationCollisionMovesLegacyPackToAvailableName() throws IOException {
        UUID existingPackId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        Path legacyDir = Files.createDirectories(tempDir.resolve("resource_packs_netease"));
        Path sourcePack = createPack(legacyDir.resolve("collision.mcpack"), RESOURCE_PACK_ID, "resources");
        Files.writeString(legacyDir.resolve("collision.mcpack.key"), ENCRYPTION_KEY, StandardCharsets.UTF_8);
        Path destinationDir = Files.createDirectories(tempDir.resolve("resource_packs"));
        Path existingPack = createPack(destinationDir.resolve("collision.netease.mcpack"), existingPackId, "resources");

        ResourcePackMigration.migrate(tempDir.toFile());

        assertTrue(Files.exists(existingPack));
        assertFalse(Files.exists(sourcePack));
        assertTrue(Files.exists(destinationDir.resolve("collision.netease.1.mcpack")));
        Config config = new Config(destinationDir.resolve("packs.yml").toFile(), Config.YAML);
        assertEquals(ENCRYPTION_KEY, config.getString(RESOURCE_PACK_ID + ".key"));
    }

    @Test
    void manifestFallbackRequiresExactBasename() throws IOException {
        UUID unrelatedId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        Path legacyDir = Files.createDirectories(tempDir.resolve("resource_packs_netease"));
        Path pack = legacyDir.resolve("wrapped.mcpack");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(pack))) {
            writeManifest(zip, "fake_manifest.json", unrelatedId.toString(), "resources");
            writeManifest(zip, "wrapper/manifest.json", RESOURCE_PACK_ID.toString(), "resources");
        }
        Files.writeString(legacyDir.resolve("wrapped.mcpack.key"), ENCRYPTION_KEY, StandardCharsets.UTF_8);

        ResourcePackMigration.migrate(tempDir.toFile());

        Config config = new Config(tempDir.resolve("resource_packs/packs.yml").toFile(), Config.YAML);
        assertEquals(ENCRYPTION_KEY, config.getString(RESOURCE_PACK_ID + ".key"));
        assertFalse(config.exists(unrelatedId + ".key"));
    }

    @Test
    void invalidManifestUuidKeepsKeyBesideMigratedPack() throws IOException {
        Path legacyDir = Files.createDirectories(tempDir.resolve("resource_packs_netease"));
        Path pack = legacyDir.resolve("invalid-uuid.mcpack");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(pack))) {
            writeManifest(zip, "manifest.json", "not-a-uuid", "resources");
        }
        Files.writeString(legacyDir.resolve("invalid-uuid.mcpack.key"), ENCRYPTION_KEY, StandardCharsets.UTF_8);

        ResourcePackMigration.migrate(tempDir.toFile());

        Path migratedKey = tempDir.resolve("resource_packs/invalid-uuid.netease.mcpack.key");
        assertTrue(Files.exists(migratedKey));
        assertEquals(ENCRYPTION_KEY, Files.readString(migratedKey, StandardCharsets.UTF_8));
        Config config = new Config(tempDir.resolve("resource_packs/packs.yml").toFile(), Config.YAML);
        assertFalse(config.exists("not-a-uuid.key"));
    }

    @Test
    void invalidPackConfigStopsMigrationBeforeMovingFiles() throws IOException {
        Path legacyDir = Files.createDirectories(tempDir.resolve("resource_packs_netease"));
        Path sourcePack = createPack(legacyDir.resolve("blocked.mcpack"), RESOURCE_PACK_ID, "resources");
        Path sourceKey = Files.writeString(
                legacyDir.resolve("blocked.mcpack.key"),
                ENCRYPTION_KEY,
                StandardCharsets.UTF_8
        );
        Path destinationDir = Files.createDirectories(tempDir.resolve("resource_packs"));
        Path packConfig = Files.writeString(
                destinationDir.resolve("packs.yml"),
                "invalid: [unterminated",
                StandardCharsets.UTF_8
        );

        assertDoesNotThrow(() -> ResourcePackMigration.migrate(tempDir.toFile()));

        assertTrue(Files.exists(sourcePack));
        assertTrue(Files.exists(sourceKey));
        assertFalse(Files.exists(destinationDir.resolve("blocked.netease.mcpack")));
        assertEquals("invalid: [unterminated", Files.readString(packConfig, StandardCharsets.UTF_8));
    }

    @Test
    void invalidPackConfigStructureStopsMigrationBeforeMovingFiles() throws IOException {
        Path legacyDir = Files.createDirectories(tempDir.resolve("resource_packs_netease"));
        Path sourcePack = createPack(legacyDir.resolve("blocked.mcpack"), RESOURCE_PACK_ID, "resources");
        Path destinationDir = Files.createDirectories(tempDir.resolve("resource_packs"));
        Files.writeString(destinationDir.resolve("packs.yml"), "not-a-pack: scalar\n", StandardCharsets.UTF_8);

        assertDoesNotThrow(() -> ResourcePackMigration.migrate(tempDir.toFile()));

        assertTrue(Files.exists(sourcePack));
        assertFalse(Files.exists(destinationDir.resolve("blocked.netease.mcpack")));
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
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            writeManifest(zip, "manifest.json", uuid.toString(), moduleType);
        }
        return path;
    }

    private static void writeManifest(ZipOutputStream zip, String entryName, String uuid, String moduleType) throws IOException {
        zip.putNextEntry(new ZipEntry(entryName));
        zip.write(manifestJson(uuid, moduleType).getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String manifestJson(String uuid, String moduleType) {
        return """
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
