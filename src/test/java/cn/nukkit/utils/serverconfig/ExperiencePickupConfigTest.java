package cn.nukkit.utils.serverconfig;

import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.yaml.snakeyaml.YamlSnakeYamlConfigurer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ExperiencePickupConfigTest {
    @TempDir Path directory;

    @Test
    void defaultOnIsWrittenToNukkitMotYamlAndExplicitOffSurvivesReload() throws Exception {
        Path file = directory.resolve("nukkit-mot.yml");
        ServerConfig config = ConfigManager.create(ServerConfig.class, it -> it.configure(opt -> {
            opt.configurer(new YamlSnakeYamlConfigurer());
            opt.bindFile(file.toFile());
        }));
        config.save();
        assertTrue(config.playerSettings().experiencePickupEvent());
        assertTrue(Files.readString(file).contains("experience-pickup-event: true"));
        Files.writeString(file, "player-settings:\n  experience-pickup-event: false\n");
        config.load();
        assertFalse(config.playerSettings().experiencePickupEvent());
        config.save();
        assertTrue(Files.readString(file).contains("experience-pickup-event: false"));
    }
}
