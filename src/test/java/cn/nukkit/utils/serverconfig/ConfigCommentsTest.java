package cn.nukkit.utils.serverconfig;

import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.yaml.snakeyaml.YamlSnakeYamlConfigurer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that ConfigComments localizes comments on nested sub-config fields
 * (e.g. NetworkSettings.netherNetSettings) through a real create-apply-save round trip.
 */
class ConfigCommentsTest {

    @TempDir
    Path tempDir;

    @Test
    void appliesChineseCommentsToNestedSubConfig() throws IOException {
        String yaml = saveWithComments("chs");

        assertTrue(yaml.contains("NetherNet (WebRTC) 传输设置"), "nested section header should be localized");
        assertTrue(yaml.contains("默认与 RakNet 并行启用"), "nested enabled field should be localized");
        assertTrue(yaml.contains("运营者身份 PEM 文件"), "nested identity-file field should be localized");
        assertFalse(yaml.contains("Operator identity PEM file"), "English annotation fallback should not leak through");
    }

    @Test
    void appliesEnglishCommentsToNestedSubConfig() throws IOException {
        String yaml = saveWithComments("eng");

        assertTrue(yaml.contains("NetherNet (WebRTC) transport settings, runs alongside RakNet"), "nested section header should resolve from eng properties");
        assertTrue(yaml.contains("Seconds to wait for the WebRTC handshake"), "nested handshake field should resolve from eng properties");
    }

    private String saveWithComments(String lang) throws IOException {
        Path file = tempDir.resolve("nukkit-mot-" + lang + ".yml");
        ServerConfig config = ConfigManager.create(ServerConfig.class, (it) -> it.configure(opt -> {
            opt.configurer(new YamlSnakeYamlConfigurer());
            opt.bindFile(file.toFile());
            opt.removeOrphans(true);
        }));
        ConfigComments.apply(config, lang);
        config.save();
        return Files.readString(file, StandardCharsets.UTF_8);
    }
}
