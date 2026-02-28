package cn.nukkit.utils.config;

import cn.nukkit.utils.config.category.*;
import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.Header;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Nukkit-MOT Server Configuration (nukkit-mot.toml)
 * <p>
 * Contains advanced/MOT-specific settings organized by category.
 * Standard Minecraft settings are in server.properties.
 *
 * @author Nukkit-MOT Team
 */
@Getter
@Accessors(fluent = true)
@Header("########################################")
@Header("# Nukkit-MOT Advanced Configuration")
@Header("# Standard settings are in server.properties")
@Header("# https://github.com/MemoriesOfTime/Nukkit-MOT")
@Header("########################################")
public class ServerConfig extends OkaeriConfig {

    @Comment("Performance and threading settings")
    private PerformanceSettings performanceSettings = new PerformanceSettings();

    @Comment("Network and compression settings")
    private NetworkSettings networkSettings = new NetworkSettings();

    @Comment("Chunk loading and ticking settings")
    private ChunkSettings chunkSettings = new ChunkSettings();

    @Comment("Entity spawning and AI settings")
    private EntitySettings entitySettings = new EntitySettings();

    @Comment("World and dimension settings")
    private WorldSettings worldSettings = new WorldSettings();

    @Comment("Player data and interaction settings")
    private PlayerSettings playerSettings = new PlayerSettings();

    @Comment("Debug, logging and metrics settings")
    private DebugSettings debugSettings = new DebugSettings();

    @Comment("Game feature toggles")
    private GameFeatureSettings gameFeatureSettings = new GameFeatureSettings();

    @Comment("NetEase client support settings")
    private NeteaseSettings neteaseSettings = new NeteaseSettings();
}
