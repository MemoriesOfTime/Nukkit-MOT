package cn.nukkit.network.process.processor.common;

import cn.nukkit.GameVersion;
import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.Server;
import cn.nukkit.blockentity.BlockEntity;
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
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/**
 * 子区块按需拉取：客户端请求视野内具体子区块，去重后按 in-flight 额度提交异步序列化
 * （每 level 的 AsyncChunkThread，与整列发送同队列），完成应答经完成队列回主线程
 * {@link #tickRequests} 发送（字节/条目账本内，超限推回队头下 tick 继续）。响应按字节
 * 预算与包级缓存一致性拆分为多个包，杜绝数 MB 级单包；队列状态与流量记账见
 * {@link PlayerSubChunkRequestState}。
 * <p>
 * 线程边界：主线程在提交时快照只读引用（section 引用——writeTo 的读锁即一致性边界；
 * 方块实体 ref 列表——避开 lazy 分节索引的跨线程重建；高度图 short 数组克隆），序列化与
 * NBT 在 worker 执行；blob 跟踪（tryTrackClientCacheBlob 为玩家状态记账）与发送回主线程。
 * 应答晚于期间方块更新的窗口与整列异步发送同类，不引入新的正确性类别。
 * <p>
 * On-demand sub-chunk pull: the client requests specific sub-chunks in view; requests are
 * deduplicated and submitted for async serialization under per-player in-flight credit
 * (the per-level AsyncChunkThread, shared with whole-column sends); completed answers return
 * to the main thread via the completion queue and are sent by {@link #tickRequests} within
 * the byte/entry ledgers (over-budget answers go back to the head for the next tick).
 * Responses split into multiple packets by byte budget and packet-level cache consistency,
 * ruling out multi-MB single packets; queue state and flow accounting live in
 * {@link PlayerSubChunkRequestState}.
 * <p>
 * Thread edges: the main thread snapshots read-only references at submit (the section
 * reference — writeTo's read lock is the consistency boundary; the block-entity reference
 * list — avoiding the lazy per-section index's cross-thread rebuild; a clone of the
 * height-map array); serialization and NBT run on the worker, while blob tracking
 * (tryTrackClientCacheBlob is player-state accounting) and sending return to the main
 * thread. The window where an answer lands after a block update matches the existing
 * whole-column async send — no new correctness class.
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

    // 测试注入同步执行器；生产路径恒为 null（走 level.getAsyncChuckExecutor()）
    // / Test seam for a synchronous executor; always null in production (level.getAsyncChuckExecutor())
    static volatile ExecutorService executorOverride;

    /**
     * 处理子区块请求：去重后按剩余 in-flight 额度提交异步序列化；已有排队/完成应答、
     * 本 tick 条目/字节上限耗尽或额度不足时入队（队列有上限，超限丢弃新请求——客户端会重新
     * 请求）。工作量上界取 offset 数（O(1)，每个 offset 至多产出一个昂贵条目），宁可保守
     * 排队也不在入队判定前对最多 8192 个 offset 逐项探测区块。
     * <p>
     * Handles a sub-chunk request: deduplicated, then submitted for async serialization
     * with the remaining in-flight credit; queued when answers are already queued or
     * completed, when this tick's entry/byte ceiling is exhausted, or when the credit cannot
     * cover the request. The queue is capped; new requests are dropped over the caps
     * (the client re-requests). The work bound is the offset count (O(1); each offset
     * yields at most one expensive entry) — queue conservatively instead of probing up
     * to 8192 offsets per arrival just to make the enqueue decision.
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
        if (state.hasDeferred() || state.hasCompletions() || state.responseBytesExceeded(tick)
                || state.remainingEntryBudget(tick, subChunkEntriesPerTickBudget(player)) <= 0
                || PlayerSubChunkRequestState.requestOffsetCount(pk) > state.remainingInFlightOffsets()) {
            if (!state.tryDefer(pk)) {
                player.getServer().getLogger().debug(player.getName() + "(" + player.getId() + "): dropped sub-chunk request over deferred queue caps ("
                        + state.deferredPacketCount() + " packets / " + state.deferredOffsetCount() + " offsets queued)");
            }
            return;
        }
        submitSubChunkRequest(playerHandle, pk, Integer.MAX_VALUE);
    }

    /**
     * 每 tick 驱动（Player.checkNetwork 调用）：先在账本内发送异步完成的应答，再按剩余
     * in-flight 额度提交延期请求。不依赖 loadQueue，站立不动的玩家也持续排空。
     * <p>
     * Per-tick driver (called from Player.checkNetwork): sends async-completed answers
     * within the ledgers first, then submits deferred requests with the remaining
     * in-flight credit. Not tied to loadQueue — stationary players keep draining too.
     */
    public static void tickRequests(PlayerHandle playerHandle) {
        PlayerSubChunkRequestState state = playerHandle.getSubChunkRequestState();
        if (state == null) {
            return;
        }
        sendCompletions(playerHandle, state);
        if (!state.hasDeferred()) {
            return;
        }
        Player player = playerHandle.player;
        int tick = player.getServer().getTick();
        state.drain(
                (pk, budget) -> submitSubChunkRequest(playerHandle, pk, budget),
                state.remainingInFlightOffsets(),
                () -> !state.responseBytesExceeded(tick)
                        && state.remainingEntryBudget(tick, subChunkEntriesPerTickBudget(player)) > 0);
    }

    /**
     * 每玩家每 tick 子区块序列化条目预算（直读配置，钳制 ≥1）。异步化后记账在发送侧，
     * 对外的投递节奏（每 tick 条目数/字节数）与原同步路径一致。
     * <p>
     * Per-player per-tick sub-chunk entry budget (live config read, clamped ≥1). With
     * serialization off the main thread the ledger applies at send time; the delivery
     * pacing (entries/bytes per tick) matches the old synchronous path.
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
     * 提交至多 {@code maxOffsets} 个 offset 的异步序列化：主线程快照（section 引用/方块
     * 实体列表/高度图克隆），worker 构建条目并回传完成队列；未提交的剩余 offset 重排队尾。
     * 提交批次保守钳制到剩余条目预算，保证执行器不可用时同步序列化也有工作量上限。
     * 返回消耗的 in-flight 额度（跨维度迟到请求不占额度，直接空响应）。
     * <p>
     * Submits up to {@code maxOffsets} offsets for async serialization: the main thread
     * snapshots (section reference / block-entity list / height-map clone), the worker
     * builds entries and returns them via the completion queue; unsubmitted offsets are
     * requeued at the tail. Batches are conservatively capped by the remaining entry
     * budget to bound synchronous work if the executor is unavailable.
     * Returns the in-flight credit consumed (a stale cross-dimension
     * request consumes none and answers empty right away).
     */
    private static int submitSubChunkRequest(PlayerHandle playerHandle, SubChunkRequestPacket pk, int maxOffsets) {
        Player player = playerHandle.player;
        PlayerSubChunkRequestState state = playerHandle.getSubChunkRequestState();
        boolean cacheMode = player.isClientBlobCacheActive();
        Level level = player.getLevel();
        List<BlockVector3> offsets = subChunkRequestOffsets(pk);
        int limit = Math.min(maxOffsets, offsets.size());

        if (level == null || pk.dimension != level.getDimension()) {
            // 跨维度迟到请求：空响应 / Stale cross-dimension request: empty response
            player.dataPacket(newSubChunkResponse(pk, cacheMode));
            return Math.max(1, limit);
        }

        // 提交前保守限制 offset 数，执行器拒绝时同步回退也不能超额序列化。
        // Bound offsets before submission so a rejected executor cannot trigger excess synchronous work.
        limit = Math.min(limit, state.remainingEntryBudget(player.getServer().getTick(),
                subChunkEntriesPerTickBudget(player)));
        int granted = state.acquireInFlightOffsets(limit);
        if (granted <= 0) {
            // 不可达（handle 守卫与 drain 记账保证 granted ≥ 1），守卫松动时也不丢请求
            // / Unreachable (the handle guard and drain bookkeeping keep granted >= 1);
            // a loosened guard still must not drop the request
            requeueSubChunkRequestResidual(state, pk, offsets, 0);
            return 0;
        }

        SubChunkTask task = buildSubChunkTask(playerHandle, state, pk, offsets, granted, cacheMode, level);
        ExecutorService executor = executorOverride != null ? executorOverride : level.getAsyncChuckExecutor();
        if (executor == null) {
            // async-chunks=false：该 level 无异步执行器，同步兜底（与执行器关停同路径）
            // / async-chunks=false: the level has no async executor — synchronous
            // fallback (same path as a shut-down executor)
            dispatchAnswer(playerHandle, state, buildAnswerSafely(task));
        } else {
            try {
                executor.execute(() -> runSerializationTask(task));
            } catch (RejectedExecutionException e) {
                // 执行器已关停（世界卸载/服停中）：同步兜底，不让请求丢失
                // / Executor shut down (level unloading / server stopping): synchronous
                // fallback so the request is not lost
                dispatchAnswer(playerHandle, state, buildAnswerSafely(task));
            }
        }
        if (granted < offsets.size()) {
            requeueSubChunkRequestResidual(state, pk, offsets, granted);
        }
        return granted;
    }

    /**
     * 主线程快照：每个 offset 解析出廉价结果（越界/未加载/全空气）或序列化输入（section
     * 引用 + 方块实体 ref 列表 + 高度图克隆）；同时冻结提交时 epoch，worker 不再回读状态。
     * 视野检查用原生 long 键（免装箱）。
     * <p>
     * Main-thread snapshot: per offset, resolves a cheap result (out of bounds / not
     * loaded / all air) or the serialization inputs (section reference + block-entity
     * reference list + height-map clone); the submit-time epoch is frozen here too, so the
     * worker never reads state back. The view check uses the native long key (no boxing).
     */
    private static SubChunkTask buildSubChunkTask(PlayerHandle playerHandle, PlayerSubChunkRequestState state,
                                                  SubChunkRequestPacket pk, List<BlockVector3> offsets,
                                                  int count, boolean cacheMode, Level level) {
        Player player = playerHandle.player;
        DimensionData dimensionData = level.getDimensionData();
        int minSectionY = dimensionData.getMinSectionY();
        int maxSectionIndex = (dimensionData.getHeight() >> 4) - 1;
        GameVersion gameVersion = playerHandle.getGameVersion();
        boolean antiXray = level.antiXrayEnabled();

        SubChunkOffsetData[] data = new SubChunkOffsetData[count];
        for (int i = 0; i < count; i++) {
            BlockVector3 offset = offsets.get(i);
            int chunkX = pk.subChunkPosition.x + offset.x;
            int chunkZ = pk.subChunkPosition.z + offset.z;
            int subIndex = pk.subChunkPosition.y + offset.y - minSectionY;

            if (subIndex < 0 || subIndex > maxSectionIndex) {
                data[i] = SubChunkOffsetData.cheap(offset, subIndex, SubChunkRequestResult.INDEX_OUT_OF_BOUNDS);
                continue;
            }
            // 限制在玩家视野内，兼防越界探测 / Restricted to the player's view; also blocks probing
            long chunkHash = Level.chunkHash(chunkX, chunkZ);
            boolean inView = player.usedChunks instanceof Long2ObjectMap<?> chunks
                    ? chunks.containsKey(chunkHash) : player.usedChunks.containsKey(chunkHash);
            BaseFullChunk chunk = inView
                    ? level.getChunkIfLoaded(chunkX, chunkZ) : null;
            if (!(chunk instanceof BaseChunk)) {
                data[i] = SubChunkOffsetData.cheap(offset, subIndex, SubChunkRequestResult.CHUNK_NOT_FOUND);
                continue;
            }
            short[] heights = chunk.getHeightMapArray().clone();
            ChunkSection[] sections = ((BaseChunk) chunk).getSections();
            if (subIndex >= sections.length || sections[subIndex] == null || sections[subIndex].isEmpty()) {
                data[i] = SubChunkOffsetData.allAir(offset, subIndex, heights);
                continue;
            }
            data[i] = SubChunkOffsetData.serialize(offset, subIndex, sections[subIndex], heights,
                    chunk.getBlockEntitiesOfSection(subIndex + minSectionY));
        }
        return new SubChunkTask(state, state.epoch(), pk, cacheMode, gameVersion, antiXray, minSectionY,
                player.getName() + "(" + player.getId() + ")", data);
    }

    /**
     * worker 入口：构建完成应答并入队回主线程；任何异常都收敛为空条目应答（额度照常归还，
     * 不楔死该玩家的管线）。
     * <p>
     * Worker entry: builds the completion and queues it back to the main thread; any
     * failure collapses into an empty-entry answer (the credit is still released, so the
     * player's pipeline cannot wedge).
     */
    private static void runSerializationTask(SubChunkTask task) {
        task.state.offerCompletion(buildAnswerSafely(task));
    }

    private static PlayerSubChunkRequestState.CompletedSubChunkAnswer buildAnswerSafely(SubChunkTask task) {
        try {
            return buildAnswer(task);
        } catch (Throwable t) {
            Server.getInstance().getLogger().error("Sub-chunk serialization failed for " + task.playerName, t);
            return new PlayerSubChunkRequestState.CompletedSubChunkAnswer(task.epoch, task.request,
                    task.cacheMode, new ArrayList<>(), null, null, null, task.offsets.length);
        }
    }

    /**
     * 构建全部条目（worker 或同步兜底）：廉价结果直接落，SUCCESS 条目编码 section +
     * 方块实体 NBT + 高度图；cache 模式额外记录 section 字节与哈希，blobId/data 的最终
     * 组装在主线程发送时完成。
     * <p>
     * Builds every entry (worker or synchronous fallback): cheap results fall through
     * directly, SUCCESS entries encode section + block-entity NBT + height map; cache
     * mode additionally records the section bytes and hash — blobId/data assembly is
     * finalized on the main thread at send time.
     */
    static PlayerSubChunkRequestState.CompletedSubChunkAnswer buildAnswer(SubChunkTask task) {
        int n = task.offsets.length;
        List<SubChunkPacket.SubChunkData> entries = new ArrayList<>(n);
        long[] hashes = task.cacheMode ? new long[n] : null;
        byte[][] sections = task.cacheMode ? new byte[n][] : null;
        byte[][] entities = task.cacheMode ? new byte[n][] : null;
        BinaryStream stream = ThreadCache.acquireBinaryStream();
        try {
            for (int i = 0; i < n; i++) {
                SubChunkOffsetData od = task.offsets[i];
                SubChunkPacket.SubChunkData entry = new SubChunkPacket.SubChunkData();
                entry.offset = od.offset;
                if (od.preset != null) {
                    entry.result = od.preset;
                    if (od.preset == SubChunkRequestResult.SUCCESS_ALL_AIR) {
                        computeHeightMap(entry, od.heights, od.subIndex, task.minSectionY);
                    }
                } else {
                    stream.reset();
                    od.section.writeTo(task.gameVersion, stream, task.antiXray);
                    byte[] sectionBytes = task.cacheMode ? stream.getBuffer() : null;
                    byte[] entityBytes = NetworkChunkSerializer.serializeBlockEntities(od.blockEntities, task.gameVersion.getProtocol());
                    computeHeightMap(entry, od.heights, od.subIndex, task.minSectionY);
                    entry.result = SubChunkRequestResult.SUCCESS;
                    stream.put(entityBytes);
                    entry.data = stream.getBuffer();
                    if (task.cacheMode) {
                        hashes[i] = XXHash64.hash(sectionBytes);
                        sections[i] = sectionBytes;
                        entities[i] = entityBytes;
                    }
                }
                entries.add(entry);
            }
        } finally {
            ThreadCache.releaseBinaryStream(stream);
        }
        return new PlayerSubChunkRequestState.CompletedSubChunkAnswer(task.epoch, task.request,
                task.cacheMode, entries, hashes, sections, entities, n);
    }

    /**
     * 发送已完成的应答（主线程）：账本内逐应答派发；字节或条目预算耗尽即停（队头保留，
     * 下 tick 继续）。epoch 不符的在途回包直接丢弃且不归还额度（下线/切维度作废；clear() 已
     * 把计数清零，再减会扣到新 epoch 提交的额度）。
     * <p>
     * Sends completed answers (main thread): dispatched one by one within the ledgers;
     * stops once the byte or entry budget is exhausted (the head stays for the next
     * tick). A stale-epoch completion is dropped without releasing credit (invalidated
     * by quit / dimension switch; clear() already zeroed the counter — releasing again
     * would take credit owned by new-epoch submissions).
     */
    private static void sendCompletions(PlayerHandle playerHandle, PlayerSubChunkRequestState state) {
        Player player = playerHandle.player;
        int tick = player.getServer().getTick();
        while (!state.responseBytesExceeded(tick)
                && state.remainingEntryBudget(tick, subChunkEntriesPerTickBudget(player)) > 0) {
            PlayerSubChunkRequestState.CompletedSubChunkAnswer answer = state.pollCompletion();
            if (answer == null) {
                return;
            }
            if (answer.epoch != state.epoch()) {
                continue;
            }
            dispatchAnswer(playerHandle, state, answer);
        }
    }

    /**
     * 派发一个完成应答：cache 模式先做 blob 跟踪（成功→条目改 blob 形态；pending 打满→
     * 降级 raw，翻包是粘性的），随后按原字节预算/包级缓存一致性拆包发送。条目预算只对
     * 昂贵条目生效；预算中途耗尽的剩余条目构造残余应答推回队头，已发送部分归还 in-flight
     * 额度。全廉价应答在预算 0 时也整包发送（与原同步路径一致——廉价条目不占预算）。
     * <p>
     * Dispatches one completion: cache mode tracks blobs first (success rewrites the
     * entry into blob form; a full pending cap degrades to raw, and the flip is sticky),
     * then sends under the original byte budget and packet-level cache consistency. The
     * entry budget applies to expensive entries only; entries left over when it runs out
     * form a residual answer pushed back to the head, and the sent portion releases its
     * in-flight credit. An all-cheap answer still sends in full at budget 0 (matching the
     * old synchronous path — cheap entries cost no budget).
     */
    private static void dispatchAnswer(PlayerHandle playerHandle, PlayerSubChunkRequestState state,
                                       PlayerSubChunkRequestState.CompletedSubChunkAnswer answer) {
        Player player = playerHandle.player;
        int tick = player.getServer().getTick();
        int stopAt = state.remainingEntryBudget(tick, subChunkEntriesPerTickBudget(player));
        List<SubChunkPacket.SubChunkData> entries = answer.entries;

        SubChunkResponseBuffer buffer = new SubChunkResponseBuffer(answer.request, answer.cacheMode, player::dataPacket);
        int sent = 0;
        int spent = 0;
        for (; sent < entries.size(); sent++) {
            SubChunkPacket.SubChunkData entry = entries.get(sent);
            if (entry.result == SubChunkRequestResult.SUCCESS && (spent >= stopAt || state.responseBytesExceeded(tick))) {
                break;
            }
            boolean trackBlob = buffer.isCacheMode();
            if (trackBlob && entry.result == SubChunkRequestResult.SUCCESS && answer.blobHashes[sent] != 0) {
                if (player.tryTrackClientCacheBlob(answer.blobHashes[sent], answer.blobSections[sent])) {
                    entry.blobId = answer.blobHashes[sent];
                    entry.hasBlobId = true;
                    entry.data = answer.blobEntities[sent];
                } else if (answer.blobEntities[sent].length == 0) {
                    entry.data = answer.blobSections[sent];
                }
                // else: pending 超限降级 raw —— data 已是 section+entities 拼接形态 / pending
                // over cap degrades to raw — data already holds the section+entities assembly
            }
            if (entry.result == SubChunkRequestResult.SUCCESS) {
                spent++;
                if (trackBlob && !entry.hasBlobId) {
                    buffer.degradeToRaw();
                }
            }
            state.accountResponseBytes(tick, buffer.add(entry));
        }
        buffer.flush();
        state.accountSpentEntries(tick, spent);
        state.releaseInFlightOffsets(sent == entries.size() ? answer.offsets : sent);

        if (sent < entries.size()) {
            state.pushCompletionFront(residual(answer, sent));
        }
    }

    /**
     * 预算耗尽后的剩余条目构造残余应答（切掉已发送前缀，blob 材料同步切片）。
     * <p>
     * Builds the residual answer for budget-blocked entries (the sent prefix is dropped,
     * the blob parts are sliced to match).
     */
    private static PlayerSubChunkRequestState.CompletedSubChunkAnswer residual(
            PlayerSubChunkRequestState.CompletedSubChunkAnswer answer, int from) {
        List<SubChunkPacket.SubChunkData> rest = new ArrayList<>(answer.entries.subList(from, answer.entries.size()));
        long[] hashes = null;
        byte[][] sections = null;
        byte[][] entities = null;
        if (answer.blobHashes != null) {
            int n = rest.size();
            hashes = new long[n];
            sections = new byte[n][];
            entities = new byte[n][];
            System.arraycopy(answer.blobHashes, from, hashes, 0, n);
            System.arraycopy(answer.blobSections, from, sections, 0, n);
            System.arraycopy(answer.blobEntities, from, entities, 0, n);
        }
        return new PlayerSubChunkRequestState.CompletedSubChunkAnswer(answer.epoch, answer.request,
                answer.cacheMode, rest, hashes, sections, entities, rest.size());
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

    /**
     * 一次异步序列化任务：提交时（主线程）确定的只读输入。section 不拷贝——writeTo 的
     * section 读锁（LevelDB readLock / Anvil sectionLock.readLock）即一致性边界；epoch 与
     * 方块实体列表同为提交时快照，worker 不回读状态（分节索引只在主线程重建）。
     * <p>
     * One async serialization task: the read-only inputs fixed at submit (main thread).
     * The section is not copied — writeTo's section read lock (LevelDB readLock / Anvil
     * sectionLock.readLock) is the consistency boundary; the epoch and the block-entity
     * list are submit-time snapshots too, so the worker never reads state back (the
     * per-section index is only rebuilt on the main thread).
     */
    static final class SubChunkTask {
        final PlayerSubChunkRequestState state;
        final int epoch;
        final SubChunkRequestPacket request;
        final boolean cacheMode;
        final GameVersion gameVersion;
        final boolean antiXray;
        final int minSectionY;
        final String playerName;
        final SubChunkOffsetData[] offsets;

        SubChunkTask(PlayerSubChunkRequestState state, int epoch, SubChunkRequestPacket request, boolean cacheMode,
                     GameVersion gameVersion, boolean antiXray, int minSectionY, String playerName,
                     SubChunkOffsetData[] offsets) {
            this.state = state;
            this.epoch = epoch;
            this.request = request;
            this.cacheMode = cacheMode;
            this.gameVersion = gameVersion;
            this.antiXray = antiXray;
            this.minSectionY = minSectionY;
            this.playerName = playerName;
            this.offsets = offsets;
        }
    }

    /**
     * 单个 offset 的快照：preset 非空为廉价结果（越界/未加载/全空气），否则为待序列化的
     * section + 方块实体 + 高度图。
     * <p>
     * Snapshot for one offset: a non-null preset is a cheap result (out of bounds / not
     * loaded / all air); otherwise the section + block entities + height map to serialize.
     */
    static final class SubChunkOffsetData {
        final BlockVector3 offset;
        final int subIndex;
        final SubChunkRequestResult preset;
        final ChunkSection section;
        final short[] heights;
        final List<BlockEntity> blockEntities;

        private SubChunkOffsetData(BlockVector3 offset, int subIndex, SubChunkRequestResult preset,
                                   ChunkSection section, short[] heights, List<BlockEntity> blockEntities) {
            this.offset = offset;
            this.subIndex = subIndex;
            this.preset = preset;
            this.section = section;
            this.heights = heights;
            this.blockEntities = blockEntities;
        }

        static SubChunkOffsetData cheap(BlockVector3 offset, int subIndex, SubChunkRequestResult preset) {
            return new SubChunkOffsetData(offset, subIndex, preset, null, null, null);
        }

        static SubChunkOffsetData allAir(BlockVector3 offset, int subIndex, short[] heights) {
            return new SubChunkOffsetData(offset, subIndex, SubChunkRequestResult.SUCCESS_ALL_AIR, null, heights, null);
        }

        static SubChunkOffsetData serialize(BlockVector3 offset, int subIndex, ChunkSection section,
                                            short[] heights, List<BlockEntity> blockEntities) {
            return new SubChunkOffsetData(offset, subIndex, null, section, heights, blockEntities);
        }
    }

    /**
     * 高度图数组的直读版（热路径）：单次取引用循环内直读，替代每条目 256 次虚调用；
     * {@code heights} 为主线程克隆快照，元素语义与 {@link BaseFullChunk#getHeightMapArray}
     * 一致（存档值，需加 minSectionY << 4 还原世界 Y）。列高全位于子区块区间之上整图
     * TOO_HIGH、之下整图 TOO_LOW，否则 HAS_DATA 写相对值（列高 − 子区块世界 Y），
     * 区间外标记 16 / -1。
     * <p>
     * Array-direct variant for the hot path: one reference fetch and in-loop array reads
     * replace 256 virtual calls per entry; {@code heights} is the main-thread clone
     * snapshot with the same element semantics as {@link BaseFullChunk#getHeightMapArray}
     * (stored values; add minSectionY << 4 for world Y). All columns above the sub-chunk's
     * range → TOO_HIGH, all below → TOO_LOW, otherwise HAS_DATA with relative values
     * (column Y − sub-chunk world Y); out-of-range columns are marked 16 / -1.
     */
    public static void computeHeightMap(SubChunkPacket.SubChunkData entry, short[] heights, int subIndex, int minSectionY) {
        int minBlockY = minSectionY << 4;
        int subWorldY = (minSectionY + subIndex) << 4;
        byte[] map = new byte[256];
        boolean higher = true;
        boolean lower = true;
        for (int i = 0; i < 256; i++) {
            int heightY = heights[i] + minBlockY;
            int heightSub = (heightY >> 4) - minSectionY;
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
