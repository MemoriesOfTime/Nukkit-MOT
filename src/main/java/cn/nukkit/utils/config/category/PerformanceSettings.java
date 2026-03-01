package cn.nukkit.utils.config.category;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(fluent = true)
public class PerformanceSettings extends OkaeriConfig {

    @Comment("Number of async worker threads (auto = CPU cores + 1)")
    private String asyncWorkers = "auto";

    @Comment("Automatically adjust tick rate based on server load")
    private boolean autoTickRate = true;

    @Comment("Maximum tick rate when auto-adjusting")
    private int autoTickRateLimit = 20;

    @Comment("Base tick rate (1 = normal speed)")
    private int baseTickRate = 1;

    @Comment("Always tick players even when far from spawn")
    private boolean alwaysTickPlayers = false;

    @Comment("Enable thread watchdog")
    private boolean threadWatchdog = true;

    @Comment("Thread watchdog check interval (ms)")
    private int threadWatchdogTick = 60000;

    @Comment("Enable level garbage collection")
    private boolean doLevelGc = true;

    @Comment("Ticks between auto-saves (6000 = 5 minutes)")
    private int ticksPerAutosave = 6000;

    @Comment("Enable automatic level compaction")
    private boolean levelAutoCompaction = true;

    @Comment("Ticks between compaction runs (36000 = 30 minutes)")
    private int levelAutoCompactionTicks = 36000;

    @Comment("LevelDB cache size in MB")
    private int leveldbCacheMb = 80;

    @Comment("Use native LevelDB library")
    private boolean useNativeLeveldb = false;

    @Comment("Enable Spark profiler")
    private boolean enableSpark = false;
}
