package cn.nukkit.utils.config.category;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(fluent = true)
@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class ChunkSettings extends OkaeriConfig {

    @Comment("Chunks to send per tick")
    private int sendingPerTick = 4;

    @Comment("Chunks to tick per tick")
    private int tickingPerTick = 40;

    @Comment("Chunk ticking radius around players")
    private int tickingRadius = 3;

    @Comment("Chunk generation queue size")
    private int generationQueueSize = 8;

    @Comment("Chunk population queue size")
    private int generationPopulationQueueSize = 8;

    @Comment("Enable dynamic light updates")
    private boolean lightUpdates = true;

    @Comment("Clear chunk tick list on save")
    private boolean clearChunkTickList = true;

    @Comment("Spawn threshold for chunk loading")
    private int spawnThreshold = 56;

    @Comment("Cache chunks in memory")
    private boolean cacheChunks = false;

    @Comment("Use async chunk loading")
    private boolean asyncChunks = true;
}
