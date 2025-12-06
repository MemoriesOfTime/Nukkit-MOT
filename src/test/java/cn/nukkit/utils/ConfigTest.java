package cn.nukkit.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;

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

}
