package cn.nukkit.network.process.processor.common;

import cn.nukkit.GameVersion;
import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.level.DimensionData;
import cn.nukkit.level.Level;
import cn.nukkit.level.PlayerSubChunkRequestState;
import cn.nukkit.level.format.ChunkSection;
import cn.nukkit.level.format.generic.BaseChunk;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.generic.serializer.NetworkChunkSerializer;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.SubChunkPacket;
import cn.nukkit.network.protocol.SubChunkRequestPacket;
import cn.nukkit.network.protocol.types.SubChunkRequestResult;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.ThreadCache;
import cn.nukkit.utils.XXHash64;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntBinaryOperator;

/**
 * 子区块按需拉取：客户端请求视野内具体子区块，去重后在预算内应答（超预算或已有排队时
 * 入队，由 {@link #drainDeferredRequests} 每 tick 排空）。响应按字节预算与包级缓存一致性
 * 拆分为多个包，杜绝数 MB 级单包；队列状态与流量记账见 {@link PlayerSubChunkRequestState}。
 * <p>
 * On-demand sub-chunk pull: the client requests specific sub-chunks in view; requests
 * are deduplicated and answered within a per-tick entry budget (over-budget or queued
 * requests defer, drained each tick by {@link #drainDeferredRequests}). Responses split
 * into multiple packets by byte budget and packet-level cache consistency, ruling out
 * multi-MB single packets; queue state and flow accounting live in
 * {@link PlayerSubChunkRequestState}.
 *
 * @author LT_Name
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubChunkRequestProcessor extends DataPacketProcessor<SubChunkRequestPacket> {

    public static final SubChunkRequestProcessor INSTANCE = new SubChunkRequestProcessor();

    // 单个响应包的序列化字节预算，超出拆分为多个包（条目自描述，分批应答合法）
    // Serialization byte budget per response packet; larger responses split into multiple
    // packets (entries are self-describing, partial answers are legal)
    private static final int MAX_RESPONSE_PACKET_BYTES = 2 * 1024 * 1024;

    /**
     * 处理子区块请求：去重后按本 tick 剩余条目预算即时应答（预算与字节账本跨包累计，
     * 高频单 offset 请求包不能绕过）；工作量上界取 offset 数（O(1)，每个 offset 至多产出
     * 一个昂贵条目），宁可保守排队也不在入队判定前对最多 8192 个 offset 逐项探测区块。
     * 本 tick 预算或字节上限耗尽、或已有排队时入队（队列有上限，超限丢弃新请求——
     * 客户端会重新请求）。
     * <p>
     * Handles a sub-chunk request: deduplicated, then answered immediately within this
     * tick's remaining entry budget (the budget and byte ledger accumulate across
     * packets — bursts of single-offset requests cannot bypass them). The work bound
     * is the offset count (O(1); each offset yields at most one expensive entry) —
     * queue conservatively instead of probing up to 8192 offsets per arrival just to
     * make the enqueue decision. Queued when this tick's budget or byte ceiling is
     * exhausted, or behind other queued requests. The queue is capped; new requests
     * are dropped over the caps (the client re-requests).
     */
    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull SubChunkRequestPacket pk) {
        Player player = playerHandle.player;
        if (!player.isSubChunkPullMode()) {
            return;
        }
        PlayerSubChunkRequestState state = playerHandle.getSubChunkRequestState();
        // 构造器可被 mock 框架绕过，状态对象未必已初始化 / constructors can be bypassed
        // (mocks), the state object may be uninitialized
        if (state == null) {
            return;
        }
        PlayerSubChunkRequestState.dedupeRequestOffsets(pk);
        int tick = player.getServer().getTick();
        int remaining = state.remainingEntryBudget(tick, subChunkEntriesPerTickBudget(player));
        if (state.hasDeferred() || state.responseBytesExceeded(tick)
                || PlayerSubChunkRequestState.requestOffsetCount(pk) > remaining) {
            if (!state.tryDefer(pk)) {
                player.getServer().getLogger().debug(player.getName() + "(" + player.getId() + "): dropped sub-chunk request over deferred queue caps ("
                        + state.deferredPacketCount() + " packets / " + state.deferredOffsetCount() + " offsets queued)");
            }
            return;
        }
        answerSubChunkRequest(playerHandle, pk, remaining);
    }

    /**
     * 每 tick 排空延期请求（Player.checkNetwork 调用）：不依赖 loadQueue，站立不动的
     * 玩家也要持续排空。排空也走本 tick 剩余预算（即时应答已消耗的部分扣减）。
     * <p>
     * Drains deferred requests each tick (called from Player.checkNetwork): not tied to
     * loadQueue — stationary players keep draining too. The drain also runs on this
     * tick's remaining budget (what immediate answers already spent is deducted).
     */
    public static void drainDeferredRequests(PlayerHandle playerHandle) {
        PlayerSubChunkRequestState state = playerHandle.getSubChunkRequestState();
        if (state == null || !state.hasDeferred()) {
            return;
        }
        Player player = playerHandle.player;
        state.drain(
                (pk, budget) -> answerSubChunkRequest(playerHandle, pk, budget),
                state.remainingEntryBudget(player.getServer().getTick(), subChunkEntriesPerTickBudget(player)),
                () -> !state.responseBytesExceeded(player.getServer().getTick()));
    }

    /**
     * 每玩家每 tick 子区块序列化条目预算（直读配置，钳制 ≥1）。
     * <p>
     * Per-player per-tick sub-chunk serialization entry budget (live config read, clamped ≥1).
     */
    private static int subChunkEntriesPerTickBudget(Player player) {
        return Math.max(1, player.getServer().getServerConfig().chunkSettings().subChunkEntriesPerTick());
    }

    private static List<BlockVector3> subChunkRequestOffsets(SubChunkRequestPacket pk) {
        if (pk.positionOffsets.isEmpty()) {
            return Collections.singletonList(new BlockVector3(0, 0, 0));
        }
        return pk.positionOffsets;
    }

    /**
     * 在条目预算内应答子区块请求：昂贵条目（非空 section，结果 SUCCESS）逐条扣减预算，
     * 预算或本 tick 字节上限耗尽后剩余 offset 重排队尾下 tick 继续（两条账本均跨 tick
     * 清零，必有进展，不会空转；首条不豁免——高频单 offset 请求包不能借此绕过账本）。
     * 响应按字节预算与包级缓存一致性拆分为多个包（见 {@link SubChunkResponseBuffer}），
     * 杜绝数 MB 级单包与缓存包内混入降级 raw 条目。空 section→SUCCESS_ALL_AIR、
     * 未加载→CHUNK_NOT_FOUND、越界→INDEX_OUT_OF_BOUNDS；开启缓存时条目带 blob 哈希，
     * 方块实体按子区块附在条目内。廉价条目不占预算，预算 0 的全廉价请求仍整包应答。
     * <p>
     * Answers a sub-chunk request within an entry budget: expensive entries (non-empty
     * sections, result SUCCESS) consume budget one by one; once the budget or this
     * tick's byte ceiling is exhausted the remaining offsets are requeued (tail) for the
     * next tick (both ledgers reset across ticks, so progress is guaranteed — no
     * spinning; the first entry is NOT exempt — bursts of single-offset request packets
     * cannot slip past the ledgers through it). Responses split into multiple packets
     * by byte budget and packet-level cache consistency (see {@link SubChunkResponseBuffer}),
     * ruling out multi-MB single packets and degraded raw entries inside cache-enabled
     * ones. Empty→SUCCESS_ALL_AIR, unloaded→CHUNK_NOT_FOUND, out of range→
     * INDEX_OUT_OF_BOUNDS; with the cache on, entries carry a blob hash and per-sub-chunk
     * block entities ride the entry. Cheap entries cost no budget; an all-cheap request
     * still answers in full at budget 0.
     *
     * @return 消耗的预算（昂贵条目数）/ budget consumed (expensive entry count)
     */
    private static int answerSubChunkRequest(PlayerHandle playerHandle, SubChunkRequestPacket pk, int budget) {
        Player player = playerHandle.player;
        PlayerSubChunkRequestState state = playerHandle.getSubChunkRequestState();
        boolean cacheMode = player.isClientBlobCacheActive();
        Level level = player.getLevel();
        if (level == null || pk.dimension != level.getDimension()) {
            // 跨维度迟到请求：空响应 / Stale cross-dimension request: empty response
            player.dataPacket(newSubChunkResponse(pk, cacheMode));
            return 0;
        }

        DimensionData dimensionData = level.getDimensionData();
        int minSectionY = dimensionData.getMinSectionY();
        int maxSectionIndex = (dimensionData.getHeight() >> 4) - 1;
        boolean antiXray = level.antiXrayEnabled();
        int tick = player.getServer().getTick();
        int stopAt = Math.max(1, budget);

        List<BlockVector3> offsets = subChunkRequestOffsets(pk);
        SubChunkResponseBuffer buffer = new SubChunkResponseBuffer(pk, cacheMode, player::dataPacket);
        int spent = 0;
        int i = 0;
        for (; i < offsets.size(); i++) {
            if (spent >= stopAt || state.responseBytesExceeded(tick)) {
                break;
            }
            boolean trackBlob = buffer.isCacheMode();
            SubChunkPacket.SubChunkData entry = buildSubChunkEntry(playerHandle, pk, offsets.get(i),
                    minSectionY, maxSectionIndex, dimensionData, trackBlob, antiXray);
            if (entry.result == SubChunkRequestResult.SUCCESS) {
                spent++;
                if (trackBlob && !entry.hasBlobId) {
                    // pending 打满：该条目已落为 raw 形态，不得混入 cacheEnabled 包；
                    // 本批剩余条目全部 raw（翻包是粘性的，pending 只在 BlobStatus 处理时才会释放）
                    // / Pending cap hit: this entry is raw already and must not ride a
                    // cacheEnabled packet; the rest of this batch goes raw (the flip is
                    // sticky — pending only drains when BlobStatus is processed)
                    buffer.degradeToRaw();
                }
            }
            state.accountResponseBytes(tick, buffer.add(entry));
        }
        buffer.flush();
        state.accountSpentEntries(tick, spent);

        if (i < offsets.size()) {
            requeueSubChunkRequestResidual(state, pk, offsets, i);
        }
        return spent;
    }

    static SubChunkPacket newSubChunkResponse(SubChunkRequestPacket pk, boolean cacheMode) {
        SubChunkPacket resp = new SubChunkPacket();
        resp.dimension = pk.dimension;
        resp.centerPosition = pk.subChunkPosition;
        resp.cacheEnabled = cacheMode;
        return resp;
    }

    /**
     * 子区块应答的分组发送缓冲：条目按包级 cacheEnabled 一致性分批——v486 格式该开关
     * 开启时每条目必写 blobId，无 blobId 的降级 raw 条目混入会被客户端按"blob 命中、
     * data=方块实体 NBT"解析。降级是粘性的：首个 track 失败后先发出已累积的缓存条目，
     * 本批剩余全部走 raw 包。超过单包字节预算即时成包（条目自描述，分批合法）。
     * <p>
     * Grouping send buffer for sub-chunk responses: entries are batched by packet-level
     * cacheEnabled consistency — in the v486 format that switch forces a blobId on every
     * entry, so a degraded raw entry without one would be parsed by the client as
     * "blob hit, data = block entity NBT". Degradation is sticky: after the first track
     * failure the accumulated cache entries are flushed and the rest of this batch goes
     * raw. A packet over the per-packet byte budget is flushed immediately (entries are
     * self-describing, partial batches are legal).
     */
    public static final class SubChunkResponseBuffer {
        private final SubChunkRequestPacket request;
        private final Consumer<SubChunkPacket> sender;
        private boolean cacheMode;
        private SubChunkPacket packet;
        private List<SubChunkPacket.SubChunkData> entries = new ArrayList<>();
        private int bytes;

        public SubChunkResponseBuffer(SubChunkRequestPacket request, boolean cacheMode, Consumer<SubChunkPacket> sender) {
            this.request = request;
            this.cacheMode = cacheMode;
            this.sender = sender;
            this.packet = newSubChunkResponse(request, cacheMode);
        }

        public boolean isCacheMode() {
            return this.cacheMode;
        }

        /**
         * 翻转为 raw 包模式：先发出已累积的缓存条目（若有）。
         * <p>
         * Flips to raw packet mode, flushing accumulated cache entries first (if any).
         */
        public void degradeToRaw() {
            if (!this.cacheMode) {
                return;
            }
            this.flush();
            this.cacheMode = false;
            this.packet = newSubChunkResponse(this.request, false);
        }

        /**
         * 追加一个条目，返回其线上字节数（供 tick 字节记账复用）；超单包预算即时成包。
         * <p>
         * Appends an entry and returns its wire byte count (reused by per-tick
         * accounting); over the per-packet budget the packet flushes immediately.
         */
        public int add(SubChunkPacket.SubChunkData entry) {
            this.entries.add(entry);
            int entryBytes = subChunkEntryWireBytes(entry);
            this.bytes += entryBytes;
            if (this.bytes >= MAX_RESPONSE_PACKET_BYTES) {
                this.flush();
            }
            return entryBytes;
        }

        public void flush() {
            if (this.entries.isEmpty()) {
                return;
            }
            this.packet.subChunks = this.entries;
            this.sender.accept(this.packet);
            this.packet = newSubChunkResponse(this.request, this.cacheMode);
            this.entries = new ArrayList<>();
            this.bytes = 0;
        }
    }

    /**
     * 未答完的剩余 offset 重排队尾。剩余必小于刚出队的请求，无需再做上限检查。
     * <p>
     * Requeues unanswered offsets at the queue tail. The residual is always smaller
     * than the request just dequeued, so cap checks are unnecessary.
     */
    private static void requeueSubChunkRequestResidual(PlayerSubChunkRequestState state,
                                                       SubChunkRequestPacket pk, List<BlockVector3> offsets, int from) {
        SubChunkRequestPacket residual = new SubChunkRequestPacket();
        residual.dimension = pk.dimension;
        residual.subChunkPosition = pk.subChunkPosition;
        residual.positionOffsets = new ArrayList<>(offsets.subList(from, offsets.size()));
        state.requeue(residual);
    }

    /**
     * 条目近似线上尺寸：载荷 + 高度图 + 固定字段开销。
     * <p>
     * Approximate wire size of an entry: payload + height maps + fixed-field overhead.
     */
    private static int subChunkEntryWireBytes(SubChunkPacket.SubChunkData entry) {
        int bytes = 32;
        if (entry.data != null) {
            bytes += entry.data.length;
        }
        if (entry.heightMapData != null) {
            bytes += entry.heightMapData.length;
        }
        if (entry.renderHeightMapData != null && entry.renderHeightMapData != entry.heightMapData) {
            bytes += entry.renderHeightMapData.length;
        }
        return bytes;
    }

    private static SubChunkPacket.SubChunkData buildSubChunkEntry(PlayerHandle playerHandle, SubChunkRequestPacket pk, BlockVector3 offset,
                                                                  int minSectionY, int maxSectionIndex, DimensionData dimensionData,
                                                                  boolean cacheMode, boolean antiXray) {
        Player player = playerHandle.player;
        SubChunkPacket.SubChunkData entry = new SubChunkPacket.SubChunkData();
        entry.offset = offset;

        int chunkX = pk.subChunkPosition.x + offset.x;
        int chunkZ = pk.subChunkPosition.z + offset.z;
        int subIndex = pk.subChunkPosition.y + offset.y - minSectionY;

        if (subIndex < 0 || subIndex > maxSectionIndex) {
            entry.result = SubChunkRequestResult.INDEX_OUT_OF_BOUNDS;
            return entry;
        }

        // 限制在玩家视野内，兼防越界探测 / Restricted to the player's view; also blocks probing
        BaseFullChunk chunk = player.usedChunks.containsKey(Level.chunkHash(chunkX, chunkZ))
                ? player.getLevel().getChunkIfLoaded(chunkX, chunkZ) : null;
        if (!(chunk instanceof BaseChunk)) {
            entry.result = SubChunkRequestResult.CHUNK_NOT_FOUND;
            return entry;
        }

        ChunkSection[] sections = ((BaseChunk) chunk).getSections();
        if (subIndex >= sections.length || sections[subIndex] == null || sections[subIndex].isEmpty()) {
            entry.result = SubChunkRequestResult.SUCCESS_ALL_AIR;
            computeHeightMap(entry, chunk::getHeightMap, subIndex, dimensionData.getMinSectionY());
            return entry;
        }

        GameVersion gameVersion = playerHandle.getGameVersion();
        BinaryStream stream = ThreadCache.acquireBinaryStream();
        try {
            sections[subIndex].writeTo(gameVersion, stream, antiXray);
            byte[] entityBytes = NetworkChunkSerializer.serializeEntitiesForSubChunk((BaseChunk) chunk, gameVersion.getProtocol(), subIndex, minSectionY);
            computeHeightMap(entry, chunk::getHeightMap, subIndex, dimensionData.getMinSectionY());
            entry.result = SubChunkRequestResult.SUCCESS;

            if (cacheMode) {
                byte[] sectionBytes = stream.getBuffer();
                long hash = XXHash64.hash(sectionBytes);
                if (player.tryTrackClientCacheBlob(hash, sectionBytes)) {
                    entry.blobId = hash;
                    entry.hasBlobId = true;
                    entry.data = entityBytes;
                    return entry;
                }
                if (entityBytes.length == 0) {
                    entry.data = sectionBytes;
                    return entry;
                }
                // pending 超限降级 raw / pending over cap: degrade to raw
            }
            stream.put(entityBytes);
            entry.data = stream.getBuffer();
            return entry;
        } finally {
            ThreadCache.releaseBinaryStream(stream);
        }
    }

    /**
     * 子区块高度图：列高位于子区块区间之上整图 TOO_HIGH、之下整图 TOO_LOW，
     * 否则 HAS_DATA 写相对值（列高 − 子区块世界 Y），区间外标记 16 / -1。
     * <p>
     * Sub-chunk height map: all columns above the sub-chunk's range → TOO_HIGH, all
     * below → TOO_LOW, otherwise HAS_DATA with relative values (column Y − sub-chunk
     * world Y); out-of-range columns are marked 16 / -1.
     */
    public static void computeHeightMap(SubChunkPacket.SubChunkData entry, IntBinaryOperator heightAt, int subIndex, int minSectionY) {
        int subWorldY = (minSectionY + subIndex) << 4;
        byte[] map = new byte[256];
        boolean higher = true;
        boolean lower = true;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int heightY = heightAt.applyAsInt(x, z);
                int heightSub = (heightY >> 4) - minSectionY;
                int i = (z << 4) | x;
                if (heightSub > subIndex) {
                    map[i] = 16;
                    lower = false;
                } else if (heightSub < subIndex) {
                    map[i] = -1;
                    higher = false;
                } else {
                    map[i] = (byte) (heightY - subWorldY);
                    higher = false;
                    lower = false;
                }
            }
        }
        if (higher) {
            entry.heightMapType = SubChunkPacket.HeightMapDataType.TOO_HIGH;
        } else if (lower) {
            entry.heightMapType = SubChunkPacket.HeightMapDataType.TOO_LOW;
        } else {
            entry.heightMapType = SubChunkPacket.HeightMapDataType.HAS_DATA;
            entry.heightMapData = map;
        }
        entry.renderHeightMapType = entry.heightMapType;
        entry.renderHeightMapData = entry.heightMapData;
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.toNewProtocolID(ProtocolInfo.SUB_CHUNK_REQUEST_PACKET);
    }

    @Override
    public Class<? extends DataPacket> getPacketClass() {
        return SubChunkRequestPacket.class;
    }

    @Override
    public boolean isSupported(GameVersion gameVersion) {
        return gameVersion.getProtocol() >= ProtocolInfo.v1_19_10;
    }
}
