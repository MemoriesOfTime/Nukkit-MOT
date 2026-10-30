package cn.nukkit.network.process.processor.common;

import cn.nukkit.GameVersion;
import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.Server;
import cn.nukkit.level.DimensionData;
import cn.nukkit.level.Level;
import cn.nukkit.level.PlayerSubChunkRequestState;
import cn.nukkit.level.format.ChunkSection;
import cn.nukkit.level.format.generic.BaseChunk;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.network.protocol.SubChunkPacket;
import cn.nukkit.network.protocol.SubChunkRequestPacket;
import cn.nukkit.network.protocol.types.SubChunkRequestResult;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.serverconfig.ServerConfig;
import cn.nukkit.utils.serverconfig.category.ChunkSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 子区块请求异步化后的主流程红绿：提交（主线程快照）→ 同步执行器（测试注入）→ 完成队列 →
 * tickRequests 发送（预算/字节账本内）。覆盖 in-flight 门控、跨维度空响应、clear() 的
 * epoch 作废与条目预算的跨 tick 保留。
 * <p>
 * Red-green for the async sub-chunk pipeline: submit (main-thread snapshot) → the
 * synchronous test executor → the completion queue → tickRequests sending (within the
 * entry/byte ledgers). Covers in-flight gating, the stale-dimension empty answer,
 * clear()'s epoch invalidation and the entry budget's cross-tick hold-back.
 */
class SubChunkRequestAsyncFlowTest {

    private Player player;
    private PlayerHandle playerHandle;
    private PlayerSubChunkRequestState state;
    private Level level;
    private Server server;
    private AtomicInteger tick;

    @BeforeEach
    void setUp() throws Exception {
        SubChunkRequestProcessor.executorOverride = mock(ExecutorService.class);
        // 同步执行器：提交即完成，完成队列在 handle 返回前就绪
        // / Synchronous executor: submit completes immediately, the completion queue is
        // ready before handle returns
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return null;
        }).when(SubChunkRequestProcessor.executorOverride).execute(any());

        tick = new AtomicInteger(100);
        server = mock(Server.class);
        when(server.getTick()).thenAnswer(inv -> tick.get());
        ServerConfig config = mock(ServerConfig.class);
        ChunkSettings settings = mock(ChunkSettings.class);
        when(settings.subChunkEntriesPerTick()).thenReturn(2);
        when(config.chunkSettings()).thenReturn(settings);
        when(server.getServerConfig()).thenReturn(config);

        player = mock(Player.class);
        setFinal(Player.class, player, "usedChunks", new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>());
        when(player.getServer()).thenReturn(server);
        when(player.isSubChunkPullMode()).thenReturn(true);
        when(player.getName()).thenReturn("bench");
        when(player.getId()).thenReturn(1L);
        when(player.isClientBlobCacheActive()).thenReturn(false);

        level = mock(Level.class);
        when(player.getLevel()).thenReturn(level);
        when(level.getDimension()).thenReturn(0);
        when(level.antiXrayEnabled()).thenReturn(false);
        DimensionData dimensionData = mock(DimensionData.class);
        when(dimensionData.getMinSectionY()).thenReturn(-4);
        when(dimensionData.getHeight()).thenReturn(256);
        when(level.getDimensionData()).thenReturn(dimensionData);

        playerHandle = mock(PlayerHandle.class);
        setFinal(PlayerHandle.class, playerHandle, "player", player);
        state = new PlayerSubChunkRequestState();
        when(playerHandle.getSubChunkRequestState()).thenReturn(state);
        when(playerHandle.getGameVersion()).thenReturn(GameVersion.V1_26_50);
    }

    @AfterEach
    void tearDown() {
        SubChunkRequestProcessor.executorOverride = null;
    }

    @ParameterizedTest
    @ValueSource(strings = {"heights", "sections", "blockEntities"})
    void snapshotFailureReturnsOnlyItsCreditAndKeepsTheUnsubmittedTail(String stage) {
        BaseChunk chunk = chunkWithSection();
        putChunkInView(chunk);
        state.acquireInFlightOffsets(5);
        IllegalStateException failure = new IllegalStateException("controlled " + stage + " snapshot failure");
        switch (stage) {
            case "heights" -> when(chunk.getHeightMapArray()).thenThrow(failure);
            case "sections" -> when(chunk.getSections()).thenThrow(failure);
            case "blockEntities" -> when(chunk.getBlockEntitiesOfSection(anyInt())).thenThrow(failure);
            default -> throw new AssertionError(stage);
        }
        SubChunkRequestPacket request = request(-4, 1);
        request.positionOffsets.add(new BlockVector3(0, 1, 0));
        request.positionOffsets.add(new BlockVector3(0, 2, 0));

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> SubChunkRequestProcessor.INSTANCE.handle(playerHandle, request)));

        assertEquals(5, state.inFlightOffsetCount(), "only this request's reservation may be released");
        assertEquals(1, state.deferredPacketCount(), "snapshot failure keeps one fresh request");
        assertSame(request, state.peekDeferred());
        assertEquals(3, state.deferredOffsetCount(), "the never-submitted tail must survive too");
        assertFalse(state.hasCompletions());
        verify(SubChunkRequestProcessor.executorOverride, never()).execute(any());
    }

    @Test
    void snapshotErrorReturnsReservedCreditAndPreservesTheOriginalError() {
        BaseChunk chunk = chunkWithSection();
        putChunkInView(chunk);
        AssertionError failure = new AssertionError("controlled snapshot error");
        when(chunk.getHeightMapArray()).thenThrow(failure);

        assertSame(failure, assertThrows(AssertionError.class,
                () -> SubChunkRequestProcessor.INSTANCE.handle(playerHandle, request(-4, 1))));

        assertEquals(0, state.inFlightOffsetCount());
        assertEquals(1, state.deferredOffsetCount());
        assertFalse(state.hasCompletions());
    }

    @Test
    void snapshotFailureAfterClearCannotReleaseNewEpochCreditOrRestoreOldRequests() {
        BaseChunk chunk = chunkWithSection();
        putChunkInView(chunk);
        int epoch = state.epoch();
        IllegalStateException failure = new IllegalStateException("snapshot failed after clear");
        when(chunk.getHeightMapArray()).thenAnswer(inv -> {
            state.clear();
            assertEquals(7, state.acquireInFlightOffsets(7));
            throw failure;
        });

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> SubChunkRequestProcessor.INSTANCE.handle(playerHandle, request(-4, 1))));

        assertNotEquals(epoch, state.epoch());
        assertEquals(7, state.inFlightOffsetCount(), "clear already discarded the old reservation");
        assertFalse(state.hasDeferred(), "a failed old snapshot cannot recreate an old-epoch request");
        assertFalse(state.hasCompletions());
    }

    private static void setFinal(Class<?> type, Object target, String name, Object value) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    /** 装配一个真实 section 引用的 mock 区块（writeTo 放 3 字节载荷） / mock chunk holding a real section reference (writeTo emits a 3-byte payload) */
    private BaseChunk chunkWithSection() {
        ChunkSection section = mock(ChunkSection.class);
        when(section.isEmpty()).thenReturn(false);
        doAnswer(inv -> {
            ((BinaryStream) inv.getArgument(1)).put(new byte[]{1, 2, 3});
            return null;
        }).when(section).writeTo(any(), any(), anyBoolean());
        BaseChunk chunk = mock(BaseChunk.class);
        ChunkSection[] sections = new ChunkSection[24];
        sections[0] = section;
        when(chunk.getSections()).thenReturn(sections);
        when(chunk.getHeightMapArray()).thenReturn(new short[256]);
        when(chunk.getBlockEntitiesOfSection(anyInt())).thenReturn(Collections.emptyList());
        return chunk;
    }

    private SubChunkRequestPacket request(int y, int offsets) {
        SubChunkRequestPacket pk = new SubChunkRequestPacket();
        pk.dimension = 0;
        pk.subChunkPosition = new BlockVector3(3, y, 5);
        for (int i = 0; i < offsets; i++) {
            pk.positionOffsets.add(new BlockVector3(0, 0, 0));
        }
        return pk;
    }

    private void putChunkInView(BaseChunk chunk) {
        player.usedChunks.put(((long) 3 << 32) | (5 & 0xFFFFFFFFL), Boolean.TRUE);
        when(level.getChunkIfLoaded(3, 5)).thenReturn(chunk);
    }

    private List<SubChunkPacket> sentPackets() {
        ArgumentCaptor<SubChunkPacket> captor = ArgumentCaptor.forClass(SubChunkPacket.class);
        verify(player, atLeast(0)).dataPacket(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void happyPathSubmitsSerializesAndSendsNextTick() {
        putChunkInView(chunkWithSection());
        SubChunkRequestPacket pk = request(-4, 1);
        SubChunkRequestProcessor.INSTANCE.handle(playerHandle, pk);

        // 同步执行器：应答已回传，未发送前占着 in-flight 额度
        // / synchronous executor: the answer is back; it holds in-flight credit until sent
        assertTrue(state.hasCompletions());
        assertEquals(1, state.inFlightOffsetCount());

        SubChunkRequestProcessor.tickRequests(playerHandle);

        List<SubChunkPacket> sent = sentPackets();
        assertEquals(1, sent.size());
        assertEquals(1, sent.get(0).subChunks.size());
        SubChunkPacket.SubChunkData entry = sent.get(0).subChunks.get(0);
        assertEquals(SubChunkRequestResult.SUCCESS, entry.result);
        // 3 字节 section 载荷 + 空实体 NBT / 3-byte section payload + empty entity NBT
        assertArrayEquals(new byte[]{1, 2, 3}, entry.data);
        assertEquals(0, state.inFlightOffsetCount(), "sending releases the in-flight credit");
        assertFalse(state.hasCompletions());
    }

    @Test
    void serializationFailureReleasesCreditAndAllowsRetry() {
        BaseChunk chunk = chunkWithSection();
        ChunkSection section = chunk.getSections()[0];
        doThrow(new IllegalStateException("broken section"))
                .when(section).writeTo(any(), any(), anyBoolean());
        putChunkInView(chunk);
        when(server.getLogger()).thenReturn(mock(cn.nukkit.utils.MainLogger.class));

        try (var servers = mockStatic(Server.class)) {
            servers.when(Server::getInstance).thenReturn(server);
            SubChunkRequestProcessor.INSTANCE.handle(playerHandle, request(-4, 1));
        }
        assertEquals(1, state.inFlightOffsetCount());
        assertTrue(state.hasCompletions());

        SubChunkRequestProcessor.tickRequests(playerHandle);

        assertEquals(0, state.inFlightOffsetCount(), "failed serialization must release all reserved offsets");
        assertFalse(state.hasCompletions());
        putChunkInView(chunkWithSection());
        SubChunkRequestProcessor.INSTANCE.handle(playerHandle, request(-4, 1));
        SubChunkRequestProcessor.tickRequests(playerHandle);
        assertEquals(1, sentPackets().size(), "a later retry can still deliver terrain");
        assertEquals(0, state.inFlightOffsetCount());
    }

    @Test
    void exhaustedInFlightDefersInsteadOfSubmitting() {
        state.acquireInFlightOffsets(PlayerSubChunkRequestState.MAX_IN_FLIGHT_OFFSETS);
        SubChunkRequestPacket pk = request(-4, 1);

        SubChunkRequestProcessor.INSTANCE.handle(playerHandle, pk);

        assertTrue(state.hasDeferred(), "no credit: the request defers");
        assertFalse(state.hasCompletions());
        assertEquals(1, state.deferredOffsetCount());
    }

    @Test
    void clearInvalidatesInFlightCompletions() {
        putChunkInView(chunkWithSection());
        SubChunkRequestProcessor.INSTANCE.handle(playerHandle, request(-4, 1));
        assertTrue(state.hasCompletions());

        state.clear();   // 下线/切维度 / quit / dimension switch

        SubChunkRequestProcessor.tickRequests(playerHandle);
        assertTrue(sentPackets().isEmpty(), "a stale-epoch completion is dropped, not sent");
        assertEquals(0, state.inFlightOffsetCount());
    }

    @Test
    void requestDefersBehindUnsentCompletions() {
        putChunkInView(chunkWithSection());
        SubChunkRequestProcessor.INSTANCE.handle(playerHandle, request(-4, 1));
        assertTrue(state.hasCompletions());

        // FIFO：上一份应答未发送时，新请求排队而不是插队提交
        // / FIFO: while a previous answer is unsent, a new request queues instead of
        // jumping ahead
        SubChunkRequestProcessor.INSTANCE.handle(playerHandle, request(-4, 1));
        assertTrue(state.hasDeferred());
        assertEquals(1, state.deferredOffsetCount());

        // 发送完成应答后，同 tick 内排队请求被提交（下 tick 发送）
        // / once the completion is sent, the queued request submits in the same tick
        // (it sends the next tick)
        SubChunkRequestProcessor.tickRequests(playerHandle);
        assertEquals(1, sentPackets().size());
        assertTrue(state.hasCompletions());
        assertEquals(1, state.inFlightOffsetCount());
    }

    @Test
    void entryBudgetHoldsBackCompletionsUntilNextTick() {
        SubChunkRequestPacket pk = request(-4, 1);
        for (int i = 0; i < 2; i++) {
            state.acquireInFlightOffsets(1);
            state.offerCompletion(answerOf(pk, 1));
        }
        assertEquals(2, state.inFlightOffsetCount());

        // 预算 2：两份全发 / budget 2: both send
        SubChunkRequestProcessor.tickRequests(playerHandle);
        assertEquals(2, sentPackets().size());
        assertEquals(0, state.inFlightOffsetCount());

        // 同 tick 预算已耗尽：新到的完成应答留待下 tick，额度保留
        // / the budget is spent for this tick: later completions wait with their credit held
        for (int i = 0; i < 2; i++) {
            state.acquireInFlightOffsets(1);
            state.offerCompletion(answerOf(pk, 1));
        }
        SubChunkRequestProcessor.tickRequests(playerHandle);
        assertEquals(2, sentPackets().size(), "no more sends within the same tick's budget");
        assertEquals(2, state.inFlightOffsetCount(), "unsent answers keep their credit");

        tick.incrementAndGet();
        SubChunkRequestProcessor.tickRequests(playerHandle);
        assertEquals(4, sentPackets().size(), "the ledger resets across ticks");
        assertEquals(0, state.inFlightOffsetCount());
    }

    /** 手工完成应答：每份 count 个 SUCCESS 条目 / handcrafted answer: count SUCCESS entries each */
    private PlayerSubChunkRequestState.CompletedSubChunkAnswer answerOf(SubChunkRequestPacket pk, int count) {
        java.util.ArrayList<SubChunkPacket.SubChunkData> entries = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            SubChunkPacket.SubChunkData entry = new SubChunkPacket.SubChunkData();
            entry.offset = new BlockVector3(0, 0, 0);
            entry.result = SubChunkRequestResult.SUCCESS;
            entry.data = new byte[]{1, 2, 3};
            entries.add(entry);
        }
        return new PlayerSubChunkRequestState.CompletedSubChunkAnswer(state.epoch(), pk, false,
                entries, null, null, null, count);
    }

    @Test
    void staleCrossDimensionRequestAnswersEmptyWithoutCredit() {
        SubChunkRequestPacket pk = request(-4, 1);
        pk.dimension = 1;   // 与 level 维度不符 / mismatches the level dimension

        SubChunkRequestProcessor.INSTANCE.handle(playerHandle, pk);

        List<SubChunkPacket> sent = sentPackets();
        assertEquals(1, sent.size());
        assertTrue(sent.get(0).subChunks == null || sent.get(0).subChunks.isEmpty());
        assertEquals(0, state.inFlightOffsetCount(), "the stale answer consumes no credit");
        assertFalse(state.hasCompletions());
    }

    /** 排队执行器：worker 任务被冻结，模拟序列化耗时中的 clear() / queueing executor: worker tasks are frozen, simulating a clear() during serialization */
    private List<Runnable> freezeWorker() {
        List<Runnable> pending = new java.util.ArrayList<>();
        ExecutorService queueing = mock(ExecutorService.class);
        doAnswer(inv -> {
            pending.add(inv.getArgument(0));
            return null;
        }).when(queueing).execute(any());
        SubChunkRequestProcessor.executorOverride = queueing;
        return pending;
    }

    @Test
    void clearDuringInFlightSerializationDropsTheStaleAnswer() {
        List<Runnable> pending = freezeWorker();
        putChunkInView(chunkWithSection());
        SubChunkRequestProcessor.INSTANCE.handle(playerHandle, request(-4, 1));
        assertEquals(1, pending.size());
        assertFalse(state.hasCompletions());

        state.clear();   // 切维度/下线发生在 worker 完成前 / dimension switch or quit before the worker finishes

        pending.get(0).run();   // worker 完成：应答必须携带提交时 epoch / the worker finishes: the answer must carry the submit-time epoch
        SubChunkRequestProcessor.tickRequests(playerHandle);

        assertTrue(sentPackets().isEmpty(), "a mid-flight clear invalidates the answer");
        assertFalse(state.hasCompletions());
    }

    @Test
    void staleDropDoesNotStealNewEpochCredit() {
        List<Runnable> pending = freezeWorker();
        putChunkInView(chunkWithSection());
        SubChunkRequestProcessor.INSTANCE.handle(playerHandle, request(-4, 1));

        state.clear();                      // epoch 递增，在途计数清零 / epoch bump, in-flight zeroed
        state.acquireInFlightOffsets(3);    // 新 epoch 的提交占用额度 / credit now owned by new-epoch submissions

        pending.get(0).run();               // 旧 epoch 应答到达 / the stale answer arrives
        SubChunkRequestProcessor.tickRequests(playerHandle);

        assertTrue(sentPackets().isEmpty());
        assertEquals(3, state.inFlightOffsetCount(), "a dropped stale answer must not release new-epoch credit");
    }

    @Test
    void nullExecutorFallsBackToSynchronousDispatch() {
        SubChunkRequestProcessor.executorOverride = null;   // async-chunks=false：level 执行器为 null
        putChunkInView(chunkWithSection());

        SubChunkRequestProcessor.INSTANCE.handle(playerHandle, request(-4, 1));

        List<SubChunkPacket> sent = sentPackets();
        assertEquals(1, sent.size());
        assertEquals(1, sent.get(0).subChunks.size());
        assertEquals(SubChunkRequestResult.SUCCESS, sent.get(0).subChunks.get(0).result);
        assertEquals(0, state.inFlightOffsetCount(), "the synchronous fallback dispatches and releases credit");
        assertFalse(state.hasCompletions());
        assertFalse(state.hasDeferred());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void synchronousRequestsShareTheTickBudgetAndDrainAcrossTicks(boolean rejectExecution) {
        useSynchronousFallback(rejectExecution);
        BaseChunk chunk = chunkWithSection();
        putChunkInView(chunk);

        for (int i = 0; i < 5; i++) {
            SubChunkRequestProcessor.INSTANCE.handle(playerHandle, request(-4, 1));
        }

        assertEquals(2, sentPackets().size(), "separate requests share the same tick budget");
        verify(chunk.getSections()[0], times(2)).writeTo(any(), any(), anyBoolean());
        assertEquals(3, state.deferredOffsetCount());
        SubChunkRequestProcessor.tickRequests(playerHandle);
        assertEquals(2, sentPackets().size(), "draining cannot reset the same tick's budget");

        tick.incrementAndGet();
        SubChunkRequestProcessor.tickRequests(playerHandle);
        assertEquals(4, sentPackets().size());
        assertEquals(1, state.deferredOffsetCount());

        tick.incrementAndGet();
        SubChunkRequestProcessor.tickRequests(playerHandle);
        assertEquals(5, sentPackets().size());
        verify(chunk.getSections()[0], times(5)).writeTo(any(), any(), anyBoolean());
        assertFalse(state.hasDeferred());
        assertFalse(state.hasCompletions());
        assertEquals(0, state.inFlightOffsetCount());
    }

    private void useSynchronousFallback(boolean rejectExecution) {
        if (rejectExecution) {
            doThrow(new RejectedExecutionException("executor stopped"))
                    .when(SubChunkRequestProcessor.executorOverride).execute(any());
        } else {
            SubChunkRequestProcessor.executorOverride = null;
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void largeSynchronousRequestSerializesOnlyTheRemainingTickBudget(boolean rejectExecution) {
        useSynchronousFallback(rejectExecution);
        BaseChunk chunk = chunkWithSection();
        SubChunkRequestPacket pk = request(-4, 0);
        for (int i = 0; i < 5; i++) {
            player.usedChunks.put(Level.chunkHash(3 + i, 5), Boolean.TRUE);
            when(level.getChunkIfLoaded(3 + i, 5)).thenReturn(chunk);
            pk.positionOffsets.add(new BlockVector3(i, 0, 0));
        }

        SubChunkRequestProcessor.INSTANCE.handle(playerHandle, pk);

        verify(chunk.getSections()[0], times(2)).writeTo(any(), any(), anyBoolean());
        assertEquals(2, sentPackets().get(0).subChunks.size());
        assertEquals(3, state.deferredOffsetCount(), "unserialized offsets wait for a later tick");
        SubChunkRequestProcessor.tickRequests(playerHandle);
        verify(chunk.getSections()[0], times(2)).writeTo(any(), any(), anyBoolean());

        tick.incrementAndGet();
        SubChunkRequestProcessor.tickRequests(playerHandle);
        verify(chunk.getSections()[0], times(4)).writeTo(any(), any(), anyBoolean());
        assertEquals(1, state.deferredOffsetCount());
        tick.incrementAndGet();
        SubChunkRequestProcessor.tickRequests(playerHandle);

        verify(chunk.getSections()[0], times(5)).writeTo(any(), any(), anyBoolean());
        List<SubChunkPacket.SubChunkData> delivered = sentPackets().stream()
                .flatMap(packet -> packet.subChunks.stream()).toList();
        assertEquals(5, delivered.size());
        for (int i = 0; i < delivered.size(); i++) {
            assertEquals(new BlockVector3(i, 0, 0), delivered.get(i).offset);
            assertEquals(SubChunkRequestResult.SUCCESS, delivered.get(i).result);
        }
        assertFalse(state.hasDeferred());
        assertFalse(state.hasCompletions());
        assertEquals(0, state.inFlightOffsetCount());
    }

    @Test
    void partiallySentCompletionKeepsOnlyUnsentCreditAcrossTicks() {
        state.acquireInFlightOffsets(5);
        state.offerCompletion(answerOf(request(-4, 1), 5));

        SubChunkRequestProcessor.tickRequests(playerHandle);
        assertEquals(2, sentPackets().get(0).subChunks.size());
        assertEquals(3, state.inFlightOffsetCount());
        assertTrue(state.hasCompletions());

        tick.incrementAndGet();
        SubChunkRequestProcessor.tickRequests(playerHandle);
        assertEquals(2, sentPackets().get(1).subChunks.size());
        assertEquals(1, state.inFlightOffsetCount());
        assertTrue(state.hasCompletions());

        tick.incrementAndGet();
        SubChunkRequestProcessor.tickRequests(playerHandle);
        assertEquals(1, sentPackets().get(2).subChunks.size());
        assertEquals(0, state.inFlightOffsetCount());
        assertFalse(state.hasCompletions());
    }
}
