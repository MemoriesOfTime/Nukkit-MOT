package cn.nukkit.utils.serverconfig.category;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(fluent = true)
public class PerformanceSettings extends OkaeriConfig {

    @Comment("Number of async worker threads (auto = CPU cores + 1)")
    @CustomKey("async-workers")
    private String asyncWorkers = "auto";

    @Comment("Automatically adjust tick rate based on server load")
    @CustomKey("auto-tick-rate")
    private boolean autoTickRate = true;

    @Comment("Maximum tick rate when auto-adjusting")
    @CustomKey("auto-tick-rate-limit")
    private int autoTickRateLimit = 20;

    @Comment("Base tick rate (1 = normal speed)")
    @CustomKey("base-tick-rate")
    private int baseTickRate = 1;

    @Comment("Always tick players even when far from spawn")
    @CustomKey("always-tick-players")
    private boolean alwaysTickPlayers = false;

    @Comment("Enable thread watchdog")
    @CustomKey("thread-watchdog")
    private boolean threadWatchdog = true;

    @Comment("Thread watchdog check interval (ms)")
    @CustomKey("thread-watchdog-tick")
    private int threadWatchdogTick = 60000;

    @Comment("Enable level garbage collection")
    @CustomKey("do-level-gc")
    private boolean doLevelGc = true;

    @Comment("Ticks between auto-saves (6000 = 5 minutes)")
    @CustomKey("ticks-per-autosave")
    private int ticksPerAutosave = 6000;

    @Comment("Enable automatic level compaction")
    @CustomKey("level-auto-compaction")
    private boolean levelAutoCompaction = true;

    @Comment("Ticks between compaction runs (36000 = 30 minutes)")
    @CustomKey("level-auto-compaction-ticks")
    private int levelAutoCompactionTicks = 36000;

    @Comment("LevelDB cache size in MB")
    @CustomKey("leveldb-cache-mb")
    private int leveldbCacheMb = 64;

    @Comment("Use native LevelDB library")
    @CustomKey("use-native-leveldb")
    private boolean useNativeLeveldb = false;

    @Comment("Enable Spark profiler")
    @CustomKey("enable-spark")
    private boolean enableSpark = false;

    @Comment("Enable parallel level ticking (experimental)")
    @Comment("Each world runs in its own thread for better multi-world performance")
    @CustomKey("parallel-level-tick")
    private boolean parallelLevelTick = false;
}
