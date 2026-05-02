package cn.nukkit.utils;

import cn.nukkit.utils.serverconfig.ConfigMigration;
import cn.nukkit.utils.serverconfig.ServerConfig;
import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.yaml.snakeyaml.YamlSnakeYamlConfigurer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ConfigTest {

    @TempDir
    Path tempDir;

    @Test
    public void testTomlSaveAndLoad() {
        File tomlFile = tempDir.resolve("test.toml").toFile();

        // Create and save config
        Config config = new Config(tomlFile, Config.TOML);
        config.set("app-name", "Nukkit-MOT");
        config.set("version", "1.0.0");
        config.set("debug", false);

        LinkedHashMap<String, Object> settings = new LinkedHashMap<>();
        settings.put("max-players", 50);
        settings.put("view-distance", 10);
        config.set("settings", settings);

        config.save();

        // Load and verify
        Config loadedConfig = new Config(tomlFile, Config.TOML);
        Assertions.assertTrue(loadedConfig.check());
        Assertions.assertEquals("Nukkit-MOT", loadedConfig.getString("app-name"));
        Assertions.assertEquals("1.0.0", loadedConfig.getString("version"));
        Assertions.assertFalse(loadedConfig.getBoolean("debug"));

        ConfigSection settingsSection = loadedConfig.getSection("settings");
        Assertions.assertEquals(50, settingsSection.getInt("max-players"));
        Assertions.assertEquals(10, settingsSection.getInt("view-distance"));
    }

    @Test
    public void testTomlDefaultValues() {
        File tomlFile = tempDir.resolve("test-defaults.toml").toFile();

        // Create config with defaults
        LinkedHashMap<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("server-name", "Default Server");
        defaults.put("max-players", 20);
        defaults.put("motd", "A Nukkit Server");

        ConfigSection defaultSection = new ConfigSection(defaults);
        Config config = new Config(tomlFile, Config.TOML, defaultSection);

        // Verify defaults are applied
        Assertions.assertEquals("Default Server", config.getString("server-name"));
        Assertions.assertEquals(20, config.getInt("max-players"));
        Assertions.assertEquals("A Nukkit Server", config.getString("motd"));
    }

    @Test
    public void testTomlTypeChecking() {
        File tomlFile = tempDir.resolve("type-check.toml").toFile();
        Config config = new Config(tomlFile, Config.TOML);

        config.set("string-value", "text");
        config.set("int-value", 42);
        config.set("double-value", 3.14);
        config.set("boolean-value", true);
        config.set("list-value", Arrays.asList(1, 2, 3));

        LinkedHashMap<String, Object> section = new LinkedHashMap<>();
        section.put("key", "value");
        config.set("section-value", section);

        // Save and reload to ensure proper type conversion
        config.save();
        Config reloaded = new Config(tomlFile, Config.TOML);

        // Test type checking methods
        Assertions.assertTrue(reloaded.isString("string-value"));
        Assertions.assertEquals("text", reloaded.getString("string-value"));

        Assertions.assertEquals(42, reloaded.getInt("int-value"));
        Assertions.assertEquals(3.14, reloaded.getDouble("double-value"), 0.001);

        Assertions.assertTrue(reloaded.isBoolean("boolean-value"));
        Assertions.assertTrue(reloaded.getBoolean("boolean-value"));

        Assertions.assertTrue(reloaded.isList("list-value"));
        Assertions.assertEquals(3, reloaded.getList("list-value").size());

        // Test section access
        ConfigSection sectionValue = reloaded.getSection("section-value");
        Assertions.assertNotNull(sectionValue);
        Assertions.assertEquals("value", sectionValue.getString("key"));
    }

    @Test
    public void testTomlComplexStructure() {
        File tomlFile = tempDir.resolve("complex.toml").toFile();
        Config config = new Config(tomlFile, Config.TOML);

        // Create complex nested structure
        LinkedHashMap<String, Object> owner = new LinkedHashMap<>();
        owner.put("name", "Tom Preston-Werner");
        owner.put("dob", "1979-05-27");

        LinkedHashMap<String, Object> database = new LinkedHashMap<>();
        database.put("server", "192.168.1.1");
        database.put("ports", Arrays.asList(8001, 8001, 8002));
        database.put("connection_max", 5000);
        database.put("enabled", true);

        LinkedHashMap<String, Object> clients = new LinkedHashMap<>();
        clients.put("hosts", Arrays.asList("alpha", "omega"));

        config.set("title", "TOML Example");
        config.set("owner", owner);
        config.set("database", database);
        config.set("clients", clients);

        // Save and reload
        config.save();
        Config reloaded = new Config(tomlFile, Config.TOML);

        // Verify complex structure
        Assertions.assertEquals("TOML Example", reloaded.getString("title"));

        ConfigSection ownerSection = reloaded.getSection("owner");
        Assertions.assertEquals("Tom Preston-Werner", ownerSection.getString("name"));
        Assertions.assertEquals("1979-05-27", ownerSection.getString("dob"));

        ConfigSection dbSection = reloaded.getSection("database");
        Assertions.assertEquals("192.168.1.1", dbSection.getString("server"));
        Assertions.assertTrue(dbSection.getBoolean("enabled"));
        Assertions.assertEquals(5000, dbSection.getInt("connection_max"));
        Assertions.assertEquals(Arrays.asList(8001, 8001, 8002), dbSection.getIntegerList("ports"));

        ConfigSection clientsSection = reloaded.getSection("clients");
        Assertions.assertEquals(Arrays.asList("alpha", "omega"), clientsSection.getStringList("hosts"));
    }

    @Test
    public void testTomlFileDetection() {
        File tomlFile = tempDir.resolve("auto-detect.toml").toFile();

        // Create config with TOML type first to ensure file has valid content
        Config config = new Config(tomlFile, Config.TOML);
        config.set("auto-detected", true);
        config.set("format", "toml");
        config.save();

        // Reload with DETECT type (should auto-detect TOML from extension)
        Config reloaded = new Config(tomlFile, Config.DETECT);
        Assertions.assertTrue(reloaded.check());
        Assertions.assertTrue(reloaded.getBoolean("auto-detected"));
        Assertions.assertEquals("toml", reloaded.getString("format"));
    }

    @Test
    public void testConfigMigrationSpaceNameModeReplacing() {
        File propertiesFile = tempDir.resolve("server.properties").toFile();
        Config properties = new Config(propertiesFile, Config.PROPERTIES);
        properties.set("space-name-mode", "replacing");

        ServerConfig serverConfig = new ServerConfig();
        ConfigMigration migration = new ConfigMigration(properties, serverConfig);

        Assertions.assertTrue(migration.migrate());
        Assertions.assertEquals("replace", serverConfig.playerSettings().spaceNameMode());
        Assertions.assertFalse(properties.exists("space-name-mode"));
    }

    @Test
    public void testConfigMigrationSpaceNameModeDisabled() {
        File propertiesFile = tempDir.resolve("server-disabled.properties").toFile();
        Config properties = new Config(propertiesFile, Config.PROPERTIES);
        properties.set("space-name-mode", "disabled");

        ServerConfig serverConfig = new ServerConfig();
        ConfigMigration migration = new ConfigMigration(properties, serverConfig);

        Assertions.assertTrue(migration.migrate());
        Assertions.assertEquals("deny", serverConfig.playerSettings().spaceNameMode());
        Assertions.assertFalse(properties.exists("space-name-mode"));
    }

    @Test
    public void testConfigMigrationInvalidIntegerNotCrash() {
        File propertiesFile = tempDir.resolve("server-invalid-int.properties").toFile();
        Config properties = new Config(propertiesFile, Config.PROPERTIES);
        properties.set("compression-level", "not-a-number");

        ServerConfig serverConfig = new ServerConfig();
        ConfigMigration migration = new ConfigMigration(properties, serverConfig);

        Assertions.assertDoesNotThrow(migration::migrate);
        Assertions.assertEquals(5, serverConfig.networkSettings().compressionLevel());
        Assertions.assertTrue(properties.exists("compression-level"));
    }

    @Test
    public void testServerConfigDefaultValues() {
        ServerConfig config = new ServerConfig();

        // Performance
        Assertions.assertEquals("auto", config.performanceSettings().asyncWorkers());
        Assertions.assertTrue(config.performanceSettings().autoTickRate());
        Assertions.assertEquals(20, config.performanceSettings().autoTickRateLimit());
        Assertions.assertEquals(1, config.performanceSettings().baseTickRate());
        Assertions.assertTrue(config.performanceSettings().threadWatchdog());
        Assertions.assertEquals(6000, config.performanceSettings().ticksPerAutosave());

        // Network
        Assertions.assertEquals(2, config.networkSettings().zlibProvider());
        Assertions.assertEquals(5, config.networkSettings().compressionLevel());
        Assertions.assertFalse(config.networkSettings().useSnappyCompression());
        Assertions.assertEquals("active", config.networkSettings().rakCookieMode());

        // Chunk
        Assertions.assertEquals(4, config.chunkSettings().sendingPerTick());
        Assertions.assertTrue(config.chunkSettings().lightUpdates());
        Assertions.assertTrue(config.chunkSettings().asyncChunks());

        // Entity
        Assertions.assertTrue(config.entitySettings().spawnEggs());
        Assertions.assertTrue(config.entitySettings().mobAi());
        Assertions.assertTrue(config.entitySettings().autoSpawnTask());
        Assertions.assertEquals(200, config.entitySettings().ticksPerSpawns());

        // World
        Assertions.assertTrue(config.worldSettings().nether());
        Assertions.assertTrue(config.worldSettings().end());
        Assertions.assertEquals(80, config.worldSettings().portalTicks());
        Assertions.assertTrue(config.worldSettings().worlds().isEmpty());

        // Player
        Assertions.assertTrue(config.playerSettings().savePlayerData());
        Assertions.assertEquals("ignore", config.playerSettings().spaceNameMode());
        Assertions.assertEquals(15, config.playerSettings().skinChangeCooldown());

        // Debug
        Assertions.assertEquals(1, config.debugSettings().debugLevel());
        Assertions.assertTrue(config.debugSettings().automaticBugReport());

        // Game features
        Assertions.assertTrue(config.gameFeatureSettings().achievements());
        Assertions.assertTrue(config.gameFeatureSettings().enableExperimentMode());
        Assertions.assertEquals(0, config.gameFeatureSettings().multiversionMinProtocol());
        Assertions.assertEquals(-1, config.gameFeatureSettings().multiversionMaxProtocol());

        // NetEase
        Assertions.assertFalse(config.neteaseSettings().clientSupport());
        Assertions.assertFalse(config.neteaseSettings().onlyAllowNeteaseClient());
    }

    @Test
    public void testServerConfigRemovesUnknownYamlKeysOnUpdate() throws Exception {
        Path configPath = tempDir.resolve("nukkit-mot.yml");
        Files.writeString(configPath, """
                unknown-top-level:
                  enabled: true
                performance-settings:
                  async-workers: "2"
                  unknown-performance-setting: true
                network-settings:
                  compression-level: 6
                  unknown-network-setting: stale
                """, StandardCharsets.UTF_8);

        ServerConfig config = ConfigManager.create(ServerConfig.class, it -> {
            it.configure(opt -> {
                opt.configurer(new YamlSnakeYamlConfigurer());
                opt.bindFile(configPath);
                opt.removeOrphans(true);
            });
            it.load(true);
        });

        String saved = Files.readString(configPath, StandardCharsets.UTF_8);
        Assertions.assertEquals("2", config.performanceSettings().asyncWorkers());
        Assertions.assertEquals(6, config.networkSettings().compressionLevel());
        Assertions.assertFalse(saved.contains("unknown-top-level"));
        Assertions.assertFalse(saved.contains("unknown-performance-setting"));
        Assertions.assertFalse(saved.contains("unknown-network-setting"));
    }

    @Test
    public void testConfigMigrationNoExistingKeys() {
        File propertiesFile = tempDir.resolve("empty-server.properties").toFile();
        Config properties = new Config(propertiesFile, Config.PROPERTIES);

        ServerConfig serverConfig = new ServerConfig();
        ConfigMigration migration = new ConfigMigration(properties, serverConfig);

        Assertions.assertFalse(migration.migrate());
    }

    @Test
    public void testParseStringListNullAndEmpty() {
        // Replicate the parseStringList logic used in Server.java
        List<String> target = new ArrayList<>();

        // Test null input
        parseStringListHelper(null, target);
        Assertions.assertTrue(target.isEmpty());

        // Test empty string
        parseStringListHelper("", target);
        Assertions.assertTrue(target.isEmpty());

        // Test whitespace only
        parseStringListHelper("   ", target);
        Assertions.assertTrue(target.isEmpty());

        // Test normal comma-separated
        parseStringListHelper("world1, world2, world3", target);
        Assertions.assertEquals(3, target.size());
        Assertions.assertEquals("world1", target.get(0));
        Assertions.assertEquals("world2", target.get(1));
        Assertions.assertEquals("world3", target.get(2));

        // Test single value
        parseStringListHelper("singleWorld", target);
        Assertions.assertEquals(1, target.size());
        Assertions.assertEquals("singleWorld", target.get(0));
    }

    /**
     * Helper that replicates the parseStringList logic from Server.java for testing.
     */
    private static void parseStringListHelper(String input, List<String> target) {
        target.clear();
        if (input == null || input.trim().isEmpty()) {
            return;
        }
        StringTokenizer tokenizer = new StringTokenizer(input, ", ");
        while (tokenizer.hasMoreTokens()) {
            target.add(tokenizer.nextToken());
        }
    }

    @Test
    public void testSpaceNameModeOldValues() {
        // Test the spaceNameMode switch logic including old values
        Assertions.assertEquals(0, resolveSpaceMode("deny"));
        Assertions.assertEquals(0, resolveSpaceMode("disabled"));
        Assertions.assertEquals(0, resolveSpaceMode("DENY"));
        Assertions.assertEquals(0, resolveSpaceMode("Disabled"));
        Assertions.assertEquals(2, resolveSpaceMode("replace"));
        Assertions.assertEquals(2, resolveSpaceMode("replacing"));
        Assertions.assertEquals(2, resolveSpaceMode("REPLACE"));
        Assertions.assertEquals(2, resolveSpaceMode("Replacing"));
        Assertions.assertEquals(1, resolveSpaceMode("ignore"));
        Assertions.assertEquals(1, resolveSpaceMode("anything_else"));
        Assertions.assertEquals(1, resolveSpaceMode(null));
    }

    /**
     * Helper that replicates the spaceNameMode resolution logic from Server.java.
     */
    private static int resolveSpaceMode(String mode) {
        return switch (mode != null ? mode.toLowerCase(Locale.ROOT) : "ignore") {
            case "deny", "disabled" -> 0;
            case "replace", "replacing" -> 2;
            default -> 1;
        };
    }
}
