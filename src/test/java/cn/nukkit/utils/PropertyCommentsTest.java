package cn.nukkit.utils;

import cn.nukkit.utils.serverconfig.ConfigComments;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * server.properties 逐键多语言注释的写出与解析回归测试
 * <p>
 * Regression tests for per-key localized comments in properties config files.
 */
public class PropertyCommentsTest {

    @TempDir
    Path tempDir;

    @Test
    public void testCommentWrittenAboveKey() throws Exception {
        File file = tempDir.resolve("server.properties").toFile();
        Config config = new Config(file, Config.PROPERTIES);
        config.setHeader("Test Properties");
        config.setPropertyComments(Map.of("motd", "Server name shown in the server list"));
        config.set("motd", "My Server");
        config.save();

        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        Assertions.assertTrue(content.contains("# Test Properties"));
        Assertions.assertTrue(content.contains("# Server name shown in the server list\r\nmotd=My Server"),
                "comment line must directly precede its key=value pair");
    }

    @Test
    public void testMultiLineComment() throws Exception {
        File file = tempDir.resolve("multi.properties").toFile();
        Config config = new Config(file, Config.PROPERTIES);
        config.setPropertyComments(Map.of("difficulty", "World difficulty\n(0-3)"));
        config.set("difficulty", 2);
        config.save();

        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        Assertions.assertTrue(content.contains("# World difficulty\r\n# (0-3)\r\ndifficulty=2"));
    }

    @Test
    public void testParseIgnoresCommentsAndKeepsValues() throws Exception {
        File file = tempDir.resolve("roundtrip.properties").toFile();
        Config config = new Config(file, Config.PROPERTIES);
        Map<String, String> comments = new LinkedHashMap<>();
        comments.put("motd", "Server name");
        comments.put("white-list", "Enable the whitelist");
        comments.put("max-players", "Max players");
        config.setPropertyComments(comments);
        config.set("motd", "中文服务器");
        config.set("white-list", true);
        config.set("max-players", 50);
        config.save();

        Config reloaded = new Config(file, Config.PROPERTIES);
        Assertions.assertEquals("中文服务器", reloaded.getString("motd"));
        Assertions.assertTrue(reloaded.getBoolean("white-list"));
        Assertions.assertEquals("50", reloaded.get("max-players"), "properties values come back as strings");

        // Saving the reloaded config without a comment table drops comments but keeps values
        reloaded.setHeader("");
        reloaded.save();
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        Assertions.assertFalse(content.contains("Server name"), "comment entries must be dropped when no comment table is set");
        Assertions.assertFalse(content.contains("Max players"));
        Assertions.assertTrue(content.contains("motd=中文服务器"));
        Assertions.assertTrue(content.contains("white-list=on"));
    }

    @Test
    public void testNoCommentsKeepsLegacyFormat() throws Exception {
        File file = tempDir.resolve("legacy.properties").toFile();
        Config config = new Config(file, Config.PROPERTIES);
        config.set("motd", "My Server");
        config.save();

        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        Assertions.assertEquals("# Properties Config File\r\nmotd=My Server\r\n", content);
    }

    @Test
    public void testLoadPropertyCommentsFromResources() {
        Map<String, String> eng = ConfigComments.loadPropertyComments("eng");
        // Keys covering all server.properties sections, including dotted rcon.*
        Assertions.assertTrue(eng.containsKey("motd"));
        Assertions.assertTrue(eng.containsKey("language"));
        Assertions.assertTrue(eng.containsKey("gamemode"));
        Assertions.assertTrue(eng.containsKey("rcon.password"));
        Assertions.assertTrue(eng.containsKey("server-authoritative-movement"));
        // Every documented key of ServerProperties must have an English entry (fallback source)
        Assertions.assertTrue(eng.size() >= 38, "eng must document all ServerProperties keys, got " + eng.size());
        Assertions.assertTrue(eng.get("motd").startsWith("Server name"));
    }

    @Test
    public void testLocalizedOverlayWithEnglishFallback() {
        Map<String, String> chs = ConfigComments.loadPropertyComments("chs");
        Assertions.assertTrue(chs.get("motd").contains("服务器名称"), "chs translation must overlay the English fallback");

        // Keys missing from a partial translation fall back to English via the eng base
        Map<String, String> eng = ConfigComments.loadPropertyComments("eng");
        for (Map.Entry<String, String> entry : eng.entrySet()) {
            Assertions.assertTrue(chs.containsKey(entry.getKey()),
                    "chs result must cover every English key (fallback), missing: " + entry.getKey());
        }
    }

    @Test
    public void testUnknownLangFallsBackToEnglish() {
        Map<String, String> unknown = ConfigComments.loadPropertyComments("zzz");
        Assertions.assertEquals(ConfigComments.loadPropertyComments("eng"), unknown);
    }

    @Test
    public void testCanonicalKeyOrdering() throws Exception {
        File file = tempDir.resolve("order.properties").toFile();
        Files.writeString(file.toPath(), "language=eng\r\nmotd=My Server\r\nold-junk=kept\r\n");

        ConfigSection defaults = new ConfigSection();
        defaults.put("motd", "Minecraft Server");
        defaults.put("server-port", 19132);
        defaults.put("language", "eng");

        // Loading adds the missing server-port default and rewrites the file in canonical order
        Config config = new Config(file, Config.PROPERTIES, defaults);
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        int motd = content.indexOf("motd=My Server");
        int port = content.indexOf("server-port=19132");
        int lang = content.indexOf("language=eng");
        int junk = content.indexOf("old-junk=kept");
        Assertions.assertTrue(motd >= 0 && port > motd && lang > port && junk > lang,
                "known keys must follow the defaults order, unrecognized keys must come last");

        // Values survive the reorder round-trip
        Assertions.assertEquals("My Server", config.getString("motd"));
        Assertions.assertEquals("kept", config.getString("old-junk"));
    }

    @Test
    public void testUnrecognizedBlockComment() throws Exception {
        File file = tempDir.resolve("junk.properties").toFile();
        ConfigSection defaults = new ConfigSection();
        defaults.put("motd", "Minecraft Server");

        Config config = new Config(file, Config.PROPERTIES, defaults);
        config.setUnrecognizedPropertyComment("Not used by Nukkit-MOT\nDelete if unused");
        config.set("my-plugin-key", "value");
        config.save();

        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        Assertions.assertTrue(content.contains("motd=Minecraft Server\r\n"
                + "# Not used by Nukkit-MOT\r\n# Delete if unused\r\nmy-plugin-key=value"),
                "unrecognized keys must follow the separator comment lines");
    }

    @Test
    public void testNoOrderKeepsInsertionOrder() throws Exception {
        File file = tempDir.resolve("insertion.properties").toFile();
        Config config = new Config(file, Config.PROPERTIES);
        config.set("zebra", 1);
        config.set("alpha", 2);
        config.setHeader("");
        config.save();

        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        Assertions.assertEquals("zebra=1\r\nalpha=2\r\n", content,
                "without a defaults map the insertion order is preserved (plugin configs)");
    }

    @Test
    public void testReloadKeepsCanonicalOrder() throws Exception {
        File file = tempDir.resolve("reload.properties").toFile();
        Files.writeString(file.toPath(), "language=eng\r\nmotd=My Server\r\n");

        ConfigSection defaults = new ConfigSection();
        defaults.put("motd", "Minecraft Server");
        defaults.put("server-port", 19132);
        defaults.put("language", "eng");

        Config config = new Config(file, Config.PROPERTIES, defaults);
        config.reload(); // reload passes an empty defaults map and must not drop the order
        config.save();

        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        Assertions.assertTrue(content.indexOf("motd=") < content.indexOf("server-port=")
                        && content.indexOf("server-port=") < content.indexOf("language="),
                "canonical order must survive reload()");
    }

    @Test
    public void testUnrecognizedCommentLoadedFromResources() {
        String eng = ConfigComments.loadUnrecognizedPropertyComment("eng");
        Assertions.assertNotNull(eng);
        Assertions.assertTrue(eng.contains("\n"), "notice spans multiple lines");
        Assertions.assertTrue(eng.contains("not used by Nukkit-MOT"));

        String chs = ConfigComments.loadUnrecognizedPropertyComment("chs");
        Assertions.assertTrue(chs.contains("Nukkit-MOT"), "chs notice must be loaded with English fallback");

        // The special entry must not leak into the per-key comment table
        Assertions.assertFalse(ConfigComments.loadPropertyComments("eng").containsKey("__unrecognized__"));
        Assertions.assertEquals(eng, ConfigComments.loadUnrecognizedPropertyComment("zzz"));
    }
}
