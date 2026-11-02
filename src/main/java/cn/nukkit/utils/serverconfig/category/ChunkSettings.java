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

    @Comment("Pause block entities outside the existing chunk ticking range; keep queued updates for resumption")
    @Comment("Disable to restore updates in every loaded chunk. BlockEntity.alwaysTick() opts out without pinning chunks.")
    @CustomKey("block-entity-ticking-range")
    private boolean blockEntityTickingRange = true;

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

    @Comment("Use async chunk loading, sending and saving")
    @CustomKey("async-chunks")
    private boolean asyncChunks = true;

    @Comment("Max pending chunk writes per world before unloading pauses (backpressure)")
    @CustomKey("max-pending-chunk-writes")
    private int maxPendingChunkWrites = 128;

    @Comment("Enable client chunk cache (blob hashes, protocol >= 475, requires client support)")
    @CustomKey("client-cache")
    private boolean clientCache = false;

    @Comment("Server-wide shared blob store budget in MB for the client chunk cache; also the hard cap for pinned (in-flight) blobs (raise for view distance 16+ or dispersed player groups)")
    @CustomKey("blob-store-max-mb")
    private int blobStoreMaxMb = 64;

    @Comment("Per-player pending blob hash cap; over it the chunk degrades to a full send")
    @CustomKey("blob-pending-cap")
    private int blobPendingCap = 512;

    @Comment("Enable sub-chunk request pull mode (protocol >= 534)")
    @CustomKey("sub-chunk-requests")
    private boolean subChunkRequests = true;

    @Comment("Sub-chunk serialization entries per player per tick")
    @CustomKey("subchunk-entries-per-tick")
    private int subChunkEntriesPerTick = 64;
}
