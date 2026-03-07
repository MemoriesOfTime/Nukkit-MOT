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
public class ChunkSettings extends OkaeriConfig {

    @Comment("Chunks to send per tick")
    @CustomKey("sending-per-tick")
    private int sendingPerTick = 4;

    @Comment("Chunks to tick per tick")
    @CustomKey("ticking-per-tick")
    private int tickingPerTick = 40;

    @Comment("Chunk ticking radius around players")
    @CustomKey("ticking-radius")
    private int tickingRadius = 3;

    @Comment("Chunk generation queue size")
    @CustomKey("generation-queue-size")
    private int generationQueueSize = 8;

    @Comment("Chunk population queue size")
    @CustomKey("generation-population-queue-size")
    private int generationPopulationQueueSize = 8;

    @Comment("Enable dynamic light updates")
    @CustomKey("light-updates")
    private boolean lightUpdates = true;

    @Comment("Clear chunk tick list on save")
    @CustomKey("clear-chunk-tick-list")
    private boolean clearChunkTickList = true;

    @Comment("Spawn threshold for chunk loading")
    @CustomKey("spawn-threshold")
    private int spawnThreshold = 56;

    @Comment("Cache chunks in memory")
    @CustomKey("cache-chunks")
    private boolean cacheChunks = false;

    @Comment("Use async chunk loading")
    @CustomKey("async-chunks")
    private boolean asyncChunks = true;
}
