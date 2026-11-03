package cn.nukkit;

import cn.nukkit.block.BlockID;
import cn.nukkit.level.ClientCacheBlobStore;
import cn.nukkit.level.DimensionEnum;
import cn.nukkit.level.Level;
import cn.nukkit.level.PlayerBlobCacheState;
import cn.nukkit.level.PlayerSubChunkRequestState;
import cn.nukkit.level.format.generic.serializer.NetworkChunkSerializer;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.network.process.processor.common.ClientCacheBlobStatusProcessor;
import cn.nukkit.network.process.processor.common.SubChunkRequestProcessor;
import cn.nukkit.network.protocol.*;
import cn.nukkit.network.protocol.types.SubChunkRequestResult;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.XXHash64;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual YAML reload hook, shell acceptance and incoming processors; transport/terrain boundary mocked. */
class SubChunkReloadAndReentryTest {
    private static final GameVersion VERSION = GameVersion.V1_20_0;
    private static final long HASH = Level.chunkHash(0, 0);
    @TempDir Path directory;
    @BeforeAll static void initialize() { MockServer.init(); }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void acceptedPullShellMustRemainServiceableWhenReloadDisablesNewPullChunks(boolean disable) throws Exception {
        Fixture f = new Fixture(directory, false);
        f.sendShell();
        assertEquals(Boolean.TRUE, f.player.usedChunks.get(HASH), "Native accepted pull shell marks initial column ready");
        assertTrue(f.player.isSubChunkPullMode());
        if (disable) {
            f.reload(false, false);
            assertFalse(f.player.isSubChunkPullMode(), "Actual Server YAML reload replaced live mode config");
        }
        f.sent.clear();
        SubChunkRequestProcessor.INSTANCE.handle(new PlayerHandle(f.player), f.request());
        boolean answered = f.sent.stream().filter(SubChunkPacket.class::isInstance).map(SubChunkPacket.class::cast)
                .flatMap(packet -> packet.subChunks.stream())
                .anyMatch(entry -> entry.result == SubChunkRequestResult.SUCCESS);
        assertTrue(f.player.orderChunks());
        boolean retry = ((LongLinkedOpenHashSet) field(f.player, "loadQueue")).contains(HASH)
                || !Boolean.TRUE.equals(f.player.usedChunks.get(HASH));
        assertTrue(answered || retry, "Previously accepted pull shell got neither requested terrain nor fresh full retry after reload");
    }

    @Test
    void disablingClientCacheStillAnswersPreviouslyIssuedBlobMiss() throws Exception {
        Fixture f = new Fixture(directory, true);
        byte[] bytes = {3, 1, 4, 1, 5};
        long hash = XXHash64.hash(bytes);
        ClientCacheBlobStore store = f.server.getClientCacheBlobStore();
        store.put(hash, bytes);
        PlayerBlobCacheState state = new PlayerBlobCacheState(store, 16);
        field(f.player, "blobCacheState", state);
        field(f.player, "clientCacheSupported", true);
        assertTrue(state.tryOpenTransaction(new long[]{hash}, 0));
        assertTrue(f.player.isClientBlobCacheActive());
        f.reload(true, false);
        assertFalse(f.player.isClientBlobCacheActive());
        ClientCacheBlobStatusPacket status = new ClientCacheBlobStatusPacket();
        status.missHashes = new long[]{hash};
        ClientCacheBlobStatusProcessor.INSTANCE.handle(new PlayerHandle(f.player), status);
        assertTrue(f.sent.stream().anyMatch(ClientCacheMissResponsePacket.class::isInstance));
        assertEquals(0, state.getPendingSize());
    }

    @Test
    void defaultDisabledModeDoesNotAcceptUnissuedRequests() throws Exception {
        Fixture f = new Fixture(directory, false);
        f.reload(false, false);
        f.player.usedChunks.put(HASH, Boolean.TRUE);
        SubChunkRequestProcessor.INSTANCE.handle(new PlayerHandle(f.player), f.request());
        assertFalse(f.sent.stream().anyMatch(SubChunkPacket.class::isInstance));
    }

    @Test
    void rejectedShellNeverCreatesAnObligation() throws Exception {
        Fixture f = new Fixture(directory, false);
        LevelChunkPacket shell = new LevelChunkPacket();
        shell.requestSubChunks = true;
        doReturn(false).when(f.player).dataPacket(any(DataPacket.class));
        f.player.sendChunk(0, 0, shell);
        f.reload(false, false);
        doAnswer(call -> { f.sent.add(call.getArgument(0)); return true; })
                .when(f.player).dataPacket(any(DataPacket.class));
        SubChunkRequestProcessor.INSTANCE.handle(new PlayerHandle(f.player), f.request());
        assertFalse(f.sent.stream().anyMatch(SubChunkPacket.class::isInstance));
    }

    @Test
    void fullReplacementRetiresTheOldPullObligation() throws Exception {
        Fixture f = new Fixture(directory, false);
        f.sendShell();
        f.reload(false, false);
        LevelChunkPacket full = new LevelChunkPacket();
        full.data = new byte[0];
        f.player.sendChunk(0, 0, full);
        f.sent.clear();
        SubChunkRequestProcessor.INSTANCE.handle(new PlayerHandle(f.player), f.request());
        assertFalse(f.sent.stream().anyMatch(SubChunkPacket.class::isInstance));
    }

    @Test
    void worldResetRetiresAllIssuedPullObligations() throws Exception {
        Fixture f = new Fixture(directory, false);
        f.sendShell();
        f.reload(false, false);
        f.player.preSwitchLevel();
        f.sent.clear();
        SubChunkRequestProcessor.INSTANCE.handle(new PlayerHandle(f.player), f.request());
        assertFalse(f.sent.stream().anyMatch(SubChunkPacket.class::isInstance));
    }

    @Test
    void unloadRetiresIssuedPullObligation() throws Exception {
        Fixture f = new Fixture(directory, false);
        f.sendShell();
        f.reload(false, false);
        doReturn(java.util.Map.of()).when(f.level).getChunkEntities(0, 0, false);
        doNothing().when(f.level).unregisterChunkLoader(f.player, 0, 0);
        f.player.unloadChunk(0, 0);
        f.sent.clear();
        SubChunkRequestProcessor.INSTANCE.handle(new PlayerHandle(f.player), f.request());
        assertFalse(f.sent.stream().anyMatch(SubChunkPacket.class::isInstance));
    }

    @Test
    void changedChunkRetiresReceiptWhenItsSubscriptionIsRemoved() throws Exception {
        Fixture f = new Fixture(directory, false);
        f.sendShell();
        f.reload(false, false);
        f.player.onChunkChanged(f.terrain);
        f.sent.clear();
        SubChunkRequestProcessor.INSTANCE.handle(new PlayerHandle(f.player), f.request());
        assertFalse(f.sent.stream().anyMatch(SubChunkPacket.class::isInstance),
                "onChunkChanged removed usedChunks, so its receipt must not survive outside the subscription set");
    }

    @Test
    void receiptForOneChunkCannotEnableAnotherLoadedFullChunk() throws Exception {
        Fixture f = new Fixture(directory, false);
        f.sendShell();
        f.reload(false, false);
        LevelDBChunk other = LevelDBChunk.getEmptyChunk(1, 0, f.terrain.getProvider());
        other.setBlock(0, 64, 0, BlockID.STONE);
        doReturn(other).when(f.level).getChunkIfLoaded(1, 0);
        f.player.usedChunks.put(Level.chunkHash(1, 0), Boolean.TRUE);
        SubChunkRequestPacket request = f.request();
        request.subChunkPosition.x = 1;
        f.sent.clear();
        SubChunkRequestProcessor.INSTANCE.handle(new PlayerHandle(f.player), request);
        assertTrue(f.sent.stream().filter(SubChunkPacket.class::isInstance).map(SubChunkPacket.class::cast)
                .flatMap(packet -> packet.subChunks.stream())
                .noneMatch(entry -> entry.result == SubChunkRequestResult.SUCCESS));
    }

    @Test
    void nestedSubChunkCallbackCannotReuseEntryBudgetBeforeOuterFlushReturns() throws Exception {
        Fixture f = new Fixture(directory, false);
        doReturn(10).when(f.server).getTick();
        assertEquals(1, f.server.getServerConfig().chunkSettings().subChunkEntriesPerTick());
        f.sendShell();
        f.sent.clear();
        java.util.concurrent.atomic.AtomicInteger callbacks = new java.util.concurrent.atomic.AtomicInteger();
        f.useNativePacketEvents(event -> {
            if (event.getPacket() instanceof SubChunkPacket && callbacks.getAndIncrement() == 0)
                SubChunkRequestProcessor.INSTANCE.handle(new PlayerHandle(f.player), f.request());
        });
        SubChunkRequestProcessor.INSTANCE.handle(new PlayerHandle(f.player), f.request());
        PlayerSubChunkRequestState flow = (PlayerSubChunkRequestState) field(f.player, "subChunkRequestState");
        assertEquals(1, callbacks.get(), "outer entry must be reserved before its packet listener runs");
        assertEquals(1, f.sent.stream().filter(SubChunkPacket.class::isInstance).count());
        assertEquals(1, flow.deferredPacketCount());
        doReturn(11).when(f.server).getTick();
        SubChunkRequestProcessor.tickRequests(new PlayerHandle(f.player));
        assertEquals(2, f.sent.stream().filter(SubChunkPacket.class::isInstance).count());
        assertEquals(0, flow.deferredPacketCount());
    }

    @Test
    void worldResetInsideSubChunkFlushMustNotRefillOldLedgersOrQueue() throws Exception {
        Fixture f = new Fixture(directory, false);
        doReturn(10).when(f.server).getTick();
        f.sendShell();
        f.sent.clear();
        java.util.concurrent.atomic.AtomicInteger callbacks = new java.util.concurrent.atomic.AtomicInteger();
        f.useNativePacketEvents(event -> {
            if (event.getPacket() instanceof SubChunkPacket) {
                callbacks.incrementAndGet();
                f.player.preSwitchLevel();
                f.player.level = mock(Level.class);
            }
        });
        SubChunkRequestPacket request = f.request();
        request.positionOffsets.add(new BlockVector3(0, 0, 0));
        request.positionOffsets.add(new BlockVector3(0, 1, 0));
        SubChunkRequestProcessor.INSTANCE.handle(new PlayerHandle(f.player), request);
        SubChunkRequestProcessor.tickRequests(new PlayerHandle(f.player));
        assertEquals(1, callbacks.get(), "the queued request must reach its native send callback");
        PlayerSubChunkRequestState flow = (PlayerSubChunkRequestState) field(f.player, "subChunkRequestState");
        assertEquals(1, flow.remainingEntryBudget(10, 1), "old callback must not refill new-world spent ledger");
        assertEquals(0, flow.deferredPacketCount(), "old residual offsets must not reappear after world reset");
        assertFalse(flow.responseBytesExceeded(10));
    }

    @Test
    void midRequestCacheFlushRechecksBudgetSpentByNestedRequest() throws Exception {
        Fixture f = new Fixture(directory, true);
        f.reload(true, true, 4);
        doReturn(10).when(f.server).getTick();
        field(f.player, "blobCacheState", new PlayerBlobCacheState(f.server.getClientCacheBlobStore(), 1));
        field(f.player, "clientCacheSupported", true);
        for (int section = 0; section < 6; section++) {
            f.terrain.setBlock(0, 64 + section * 16, 0, BlockID.STONE);
        }
        f.sendShell();
        f.sent.clear();
        java.util.concurrent.atomic.AtomicInteger callbacks = new java.util.concurrent.atomic.AtomicInteger();
        f.useNativePacketEvents(event -> {
            if (event.getPacket() instanceof SubChunkPacket && callbacks.getAndIncrement() == 0) {
                SubChunkRequestPacket nested = f.request();
                nested.positionOffsets.add(new BlockVector3(0, 4, 0));
                nested.positionOffsets.add(new BlockVector3(0, 5, 0));
                SubChunkRequestProcessor.INSTANCE.handle(new PlayerHandle(f.player), nested);
            }
        });
        SubChunkRequestPacket outer = f.request();
        for (int section = 0; section < 4; section++) outer.positionOffsets.add(new BlockVector3(0, section, 0));
        SubChunkRequestProcessor.INSTANCE.handle(new PlayerHandle(f.player), outer);
        PlayerSubChunkRequestState flow = (PlayerSubChunkRequestState) field(f.player, "subChunkRequestState");
        assertEquals(4, successfulEntries(f), "nested flush must share the live entry ledger with its caller");
        assertEquals(0, flow.remainingEntryBudget(10, 4));
        assertEquals(2, flow.deferredOffsetCount() + flow.inFlightOffsetCount(),
                "outer residual must survive in the request or completed-answer queue until the next tick");
        doReturn(11).when(f.server).getTick();
        SubChunkRequestProcessor.tickRequests(new PlayerHandle(f.player));
        assertEquals(6, successfulEntries(f));
        assertFalse(flow.hasDeferred());
    }

    @Test
    void nestedDeferredDrainCannotReuseOuterRemainingBudget() throws Exception {
        Fixture f = new Fixture(directory, false);
        f.reload(true, false, 4);
        doReturn(10).when(f.server).getTick();
        f.sendShell();
        f.sent.clear();
        PlayerSubChunkRequestState flow = (PlayerSubChunkRequestState) field(f.player, "subChunkRequestState");
        for (int i = 0; i < 5; i++) assertTrue(flow.tryDefer(f.request()));
        java.util.concurrent.atomic.AtomicInteger callbacks = new java.util.concurrent.atomic.AtomicInteger();
        f.useNativePacketEvents(event -> {
            if (event.getPacket() instanceof SubChunkPacket && callbacks.getAndIncrement() == 0) {
                SubChunkRequestProcessor.tickRequests(new PlayerHandle(f.player));
            }
        });
        SubChunkRequestProcessor.tickRequests(new PlayerHandle(f.player));
        assertEquals(4, successfulEntries(f), "nested drain must exhaust the same per-tick entry ledger");
        assertEquals(1, flow.deferredPacketCount());
        doReturn(11).when(f.server).getTick();
        SubChunkRequestProcessor.tickRequests(new PlayerHandle(f.player));
        assertEquals(5, successfulEntries(f));
        assertFalse(flow.hasDeferred());
    }

    @Test
    void resetInsideAsyncDispatchCannotReleaseNewEpochCreditOrRefillItsLedger() throws Exception {
        Fixture f = new Fixture(directory, false);
        doReturn(10).when(f.server).getTick();
        List<Runnable> workers = captureSerialization(f);
        f.sendShell();
        f.sent.clear();
        java.util.concurrent.atomic.AtomicInteger callbacks = new java.util.concurrent.atomic.AtomicInteger();
        f.useNativePacketEvents(event -> {
            if (event.getPacket() instanceof SubChunkPacket && callbacks.getAndIncrement() == 0) {
                f.player.preSwitchLevel();
                f.player.usedChunks.put(HASH, Boolean.FALSE);
                try { f.sendShell(); } catch (Exception failure) { throw new RuntimeException(failure); }
                SubChunkRequestProcessor.INSTANCE.handle(new PlayerHandle(f.player), f.request());
            }
        });
        SubChunkRequestProcessor.INSTANCE.handle(new PlayerHandle(f.player), f.request());
        assertEquals(1, workers.size());
        workers.remove(0).run();
        SubChunkRequestProcessor.tickRequests(new PlayerHandle(f.player));
        PlayerSubChunkRequestState flow = (PlayerSubChunkRequestState) field(f.player, "subChunkRequestState");
        assertEquals(1, callbacks.get(), "the old completion must reach its send listener");
        assertEquals(1, workers.size(), "the listener must submit fresh-epoch work");
        assertEquals(1, flow.inFlightOffsetCount(), "old completion must not release the new epoch's credit");
        assertEquals(1, flow.remainingEntryBudget(10, 1), "old accounting must not refill the cleared ledger");
        f.sent.clear();
        doReturn(11).when(f.server).getTick();
        workers.remove(0).run();
        SubChunkRequestProcessor.tickRequests(new PlayerHandle(f.player));
        assertEquals(1, successfulEntries(f));
        assertEquals(0, flow.inFlightOffsetCount());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void subChunkAndMissShareTheActualByteLedgerInEitherOrder(boolean missFirst) throws Exception {
        Fixture f = new Fixture(directory, true);
        f.reload(true, true, 16);
        doReturn(10).when(f.server).getTick();
        f.sendShell();
        f.sent.clear();
        PlayerSubChunkRequestState flow = (PlayerSubChunkRequestState) field(f.player, "subChunkRequestState");
        int budget = flow.remainingResponseBytes(10);
        byte[] payload = new byte[(missFirst ? budget - 64 : 128) - 13];
        long hash = XXHash64.hash(payload);
        ClientCacheBlobStore store = f.server.getClientCacheBlobStore();
        store.put(hash, payload);
        PlayerBlobCacheState cache = new PlayerBlobCacheState(store, 16);
        field(f.player, "blobCacheState", cache);
        assertTrue(cache.tryOpenTransaction(new long[]{hash}, 10));
        ClientCacheBlobStatusPacket miss = new ClientCacheBlobStatusPacket();
        miss.missHashes = new long[]{hash};
        PlayerHandle handle = new PlayerHandle(f.player);

        if (missFirst) ClientCacheBlobStatusProcessor.INSTANCE.handle(handle, miss);
        offerRawAnswer(f, missFirst ? 128 : budget - 64);
        SubChunkRequestProcessor.tickRequests(handle);
        if (!missFirst) ClientCacheBlobStatusProcessor.INSTANCE.handle(handle, miss);

        assertEquals(64, flow.remainingResponseBytes(10), "the second producer must leave the first producer's reservation intact");
        assertEquals(missFirst ? 0 : 1, successfulEntries(f));
        assertEquals(missFirst ? 1 : 0, f.sent.stream().filter(ClientCacheMissResponsePacket.class::isInstance).count());
        assertEquals(missFirst ? 0 : 1, flow.deferredMissHashCount());
        assertEquals(missFirst, flow.hasCompletions());
        doReturn(11).when(f.server).getTick();
        ClientCacheBlobStatusProcessor.drainDeferredMissResponses(handle);
        SubChunkRequestProcessor.tickRequests(handle);
        assertEquals(1, successfulEntries(f));
        assertEquals(1, f.sent.stream().filter(ClientCacheMissResponsePacket.class::isInstance).count());
        assertFalse(flow.hasCompletions());
        assertEquals(0, flow.inFlightOffsetCount());
        assertEquals(0, cache.getPendingSize());
    }

    @Test
    void oversizedSubChunkEntryProgressesOnlyOncePerFreshTick() throws Exception {
        Fixture f = new Fixture(directory, false);
        f.reload(true, false, 16);
        doReturn(10).when(f.server).getTick();
        f.sendShell();
        f.sent.clear();
        PlayerSubChunkRequestState flow = (PlayerSubChunkRequestState) field(f.player, "subChunkRequestState");
        int budget = flow.remainingResponseBytes(10);
        offerRawAnswer(f, budget + 128, budget + 128);
        PlayerHandle handle = new PlayerHandle(f.player);
        SubChunkRequestProcessor.tickRequests(handle);
        SubChunkRequestProcessor.tickRequests(handle);
        assertEquals(1, successfulEntries(f));
        assertEquals(1, flow.inFlightOffsetCount());
        assertTrue(flow.responseBytesExceeded(10));
        doReturn(11).when(f.server).getTick();
        SubChunkRequestProcessor.tickRequests(handle);
        assertEquals(2, successfulEntries(f));
        assertEquals(0, flow.inFlightOffsetCount());
    }

    @Test
    void subChunkFlushReservesBytesBeforeANestedMissCallback() throws Exception {
        Fixture f = new Fixture(directory, true);
        f.reload(true, true, 16);
        doReturn(10).when(f.server).getTick();
        f.sendShell();
        f.sent.clear();
        PlayerSubChunkRequestState flow = (PlayerSubChunkRequestState) field(f.player, "subChunkRequestState");
        int budget = flow.remainingResponseBytes(10);
        byte[] payload = new byte[128 - 13];
        long hash = XXHash64.hash(payload);
        ClientCacheBlobStore store = f.server.getClientCacheBlobStore();
        store.put(hash, payload);
        PlayerBlobCacheState cache = new PlayerBlobCacheState(store, 16);
        field(f.player, "blobCacheState", cache);
        assertTrue(cache.tryOpenTransaction(new long[]{hash}, 10));
        PlayerHandle handle = new PlayerHandle(f.player);
        f.useNativePacketEvents(event -> {
            if (event.getPacket() instanceof SubChunkPacket) {
                ClientCacheBlobStatusPacket miss = new ClientCacheBlobStatusPacket();
                miss.missHashes = new long[]{hash};
                ClientCacheBlobStatusProcessor.INSTANCE.handle(handle, miss);
            }
        });
        offerRawAnswer(f, budget - 64);

        SubChunkRequestProcessor.tickRequests(handle);

        assertEquals(1, successfulEntries(f));
        assertEquals(64, flow.remainingResponseBytes(10));
        assertEquals(1, flow.deferredMissHashCount());
        assertEquals(0, f.sent.stream().filter(ClientCacheMissResponsePacket.class::isInstance).count());
        assertEquals(1, cache.getPendingSize());
        doReturn(11).when(f.server).getTick();
        ClientCacheBlobStatusProcessor.drainDeferredMissResponses(handle);
        assertEquals(1, f.sent.stream().filter(ClientCacheMissResponsePacket.class::isInstance).count());
        assertEquals(0, cache.getPendingSize());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void failedOrCancelledSectionRemainsMissingAfterNeighborSucceeds(boolean cancelled) throws Exception {
        Fixture f = new Fixture(directory, false);
        f.reload(true, false, 8);
        doReturn(10).when(f.server).getTick();
        f.sendShell();
        f.sent.clear();
        PlayerSubChunkRequestState flow = (PlayerSubChunkRequestState) field(f.player, "subChunkRequestState");
        if (cancelled) {
            f.useNativePacketEvents(event -> {
                if (event.getPacket() instanceof SubChunkPacket packet && packet.centerPosition.y == 4) event.setCancelled(true);
            });
        } else {
            doReturn(null).when(f.level).getChunkIfLoaded(0, 0);
        }
        PlayerHandle handle = new PlayerHandle(f.player);
        SubChunkRequestProcessor.INSTANCE.handle(handle, f.request());
        assertEquals(0, flow.inFlightOffsetCount(), "a failed attempt releases active work credit");
        assertFalse(flow.isColumnDataReady(HASH, 10, 200));
        doReturn(f.terrain).when(f.level).getChunkIfLoaded(0, 0);
        List<Runnable> workers = captureSerialization(f);
        SubChunkRequestPacket neighbor = f.request(); neighbor.subChunkPosition.y = 5;
        SubChunkRequestProcessor.INSTANCE.handle(handle, neighbor);
        SubChunkRequestProcessor.tickRequests(handle);
        // A canceled Y4 retry can already be queued: finish the distinct neighbor first.
        workers.get(workers.size() - 1).run();
        SubChunkRequestProcessor.tickRequests(handle);
        assertTrue(f.sent.stream().filter(SubChunkPacket.class::isInstance).map(SubChunkPacket.class::cast)
                .anyMatch(packet -> packet.centerPosition.y == 5 && packet.subChunks.stream()
                        .anyMatch(entry -> entry.result == SubChunkRequestResult.SUCCESS_ALL_AIR)),
                "the control neighbor must actually reach accepted transport");
        assertFalse(flow.isColumnDataReady(HASH, 10, 200), "Y=5 cannot fulfill the missing Y=4 obligation");
        if (cancelled) f.useNativePacketEvents(event -> { });
        doReturn(11).when(f.server).getTick();
        if (cancelled) workers.get(0).run();
        else {
            SubChunkRequestProcessor.INSTANCE.handle(handle, f.request());
            workers.get(workers.size() - 1).run();
        }
        SubChunkRequestProcessor.tickRequests(handle);
        assertTrue(flow.isColumnDataReady(HASH, 11, 200), "successful exact retry clears its own missing marker");
        assertEquals(0, flow.inFlightOffsetCount());
    }

    @ParameterizedTest @ValueSource(strings = {"shell", "epoch", "world"})
    void lateCompletionCannotAcknowledgeReplacementContext(String change) throws Exception {
        Fixture f = new Fixture(directory, false);
        f.reload(true, false, 8);
        doReturn(10).when(f.server).getTick();
        f.sendShell(); f.sent.clear();
        List<Runnable> workers = captureSerialization(f);
        PlayerHandle handle = new PlayerHandle(f.player);
        PlayerSubChunkRequestState flow = (PlayerSubChunkRequestState) field(f.player, "subChunkRequestState");
        SubChunkRequestPacket original = f.request();
        SubChunkRequestProcessor.INSTANCE.handle(handle, original);
        original.subChunkPosition.y = 9; // The worker owns its frozen center/offsets.
        switch (change) {
            case "shell" -> flow.onColumnSent(HASH, 10, true);
            case "epoch" -> { flow.clear(); flow.onColumnSent(HASH, 10, true); flow.acquireInFlightOffsets(1); }
            case "world" -> field(f.player, "level", mock(Level.class));
            default -> throw new AssertionError(change);
        }
        workers.get(0).run();
        SubChunkRequestProcessor.tickRequests(handle);
        assertEquals(0, successfulEntries(f), "old context must not reach accepted transport");
        assertFalse(flow.isColumnDataReady(HASH, 10, 200));
        assertEquals(change.equals("epoch") ? 1 : 0, flow.inFlightOffsetCount());
    }

    @Test
    void shellReplacementInsideAcceptedPacketCannotCompleteItsNewLedger() throws Exception {
        Fixture f = new Fixture(directory, false);
        f.reload(true, false, 8);
        f.sendShell(); f.sent.clear();
        PlayerSubChunkRequestState flow = (PlayerSubChunkRequestState) field(f.player, "subChunkRequestState");
        f.useNativePacketEvents(event -> {
            if (event.getPacket() instanceof SubChunkPacket) {
                flow.onColumnSent(HASH, 10, true);
                flow.addOutstandingOffsets(HASH, 1);
            }
        });
        SubChunkRequestProcessor.INSTANCE.handle(new PlayerHandle(f.player), f.request());
        assertFalse(flow.isColumnDataReady(HASH, 10, 200));
        flow.completeOutstandingOffsets(HASH, 1, 4);
        assertTrue(flow.isColumnDataReady(HASH, 10, 200), "only the replacement's own receipt completes it");
        assertEquals(0, flow.inFlightOffsetCount());
    }

    @Test
    void submittedCoordinatesCannotBeChangedThroughTheIncomingPacket() throws Exception {
        Fixture f = new Fixture(directory, false);
        f.reload(true, false, 8);
        f.sendShell(); f.sent.clear();
        List<Runnable> workers = captureSerialization(f);
        SubChunkRequestPacket request = f.request();
        request.positionOffsets.add(new BlockVector3(0, 0, 0));
        PlayerHandle handle = new PlayerHandle(f.player);
        SubChunkRequestProcessor.INSTANCE.handle(handle, request);
        request.subChunkPosition.y = 9;
        request.positionOffsets.get(0).y = 2;
        workers.get(0).run();
        SubChunkRequestProcessor.tickRequests(handle);
        SubChunkPacket packet = f.sent.stream().filter(SubChunkPacket.class::isInstance).map(SubChunkPacket.class::cast)
                .findFirst().orElseThrow();
        assertEquals(4, packet.centerPosition.y);
        assertEquals(0, packet.subChunks.get(0).offset.y);
        assertEquals(SubChunkRequestResult.SUCCESS, packet.subChunks.get(0).result);
    }

    @Test
    void emptyFailedCompletionRetainsItsMissingSectionAndSchedulesBoundedRetry() throws Exception {
        Fixture f = new Fixture(directory, false);
        f.reload(true, false, 8);
        f.sendShell(); f.sent.clear();
        List<Runnable> workers = captureSerialization(f);
        PlayerSubChunkRequestState flow = (PlayerSubChunkRequestState) field(f.player, "subChunkRequestState");
        assertEquals(1, flow.acquireInFlightOffsets(1));
        flow.addOutstandingOffsets(HASH, 1);
        flow.offerCompletion(new PlayerSubChunkRequestState.CompletedSubChunkAnswer(flow.epoch(), f.request(), false,
                List.of(), null, null, null, 1, f.level, java.util.Map.of(HASH, flow.columnReceipt(HASH)),
                List.of(new BlockVector3(0, 0, 0))));
        PlayerHandle handle = new PlayerHandle(f.player);
        SubChunkRequestProcessor.tickRequests(handle);
        assertEquals(0, successfulEntries(f));
        assertFalse(flow.isColumnDataReady(HASH, f.server.getTick(), 200));
        assertEquals(1, workers.size(), "abandoned work transfers to the existing bounded request path");
        assertEquals(1, flow.inFlightOffsetCount(), "only the retry owns active credit");
        workers.get(0).run();
        SubChunkRequestProcessor.tickRequests(handle);
        assertTrue(flow.isColumnDataReady(HASH, f.server.getTick(), 200));
        assertEquals(0, flow.inFlightOffsetCount());
    }

    private static void offerRawAnswer(Fixture f, int... entryBytes) throws Exception {
        PlayerSubChunkRequestState flow = (PlayerSubChunkRequestState) field(f.player, "subChunkRequestState");
        List<SubChunkPacket.SubChunkData> entries = new ArrayList<>();
        for (int size : entryBytes) {
            SubChunkPacket.SubChunkData entry = new SubChunkPacket.SubChunkData();
            entry.offset = new BlockVector3(0, 0, 0);
            entry.result = SubChunkRequestResult.SUCCESS;
            entry.data = new byte[size - 32];
            entries.add(entry);
        }
        assertEquals(entryBytes.length, flow.acquireInFlightOffsets(entryBytes.length));
        flow.offerCompletion(new PlayerSubChunkRequestState.CompletedSubChunkAnswer(flow.epoch(), f.request(), false,
                entries, null, null, null, entryBytes.length));
    }

    @Test
    void budgetBlockedCacheEntryCanStillDegradeToRawAfterItsPinExpires() throws Exception {
        Fixture f = new Fixture(directory, true);
        f.reload(true, true, 16);
        doReturn(10).when(f.server).getTick();
        f.sendShell();
        f.sent.clear();
        field(f.player, "clientCacheSupported", true);
        ClientCacheBlobStore store = f.server.getClientCacheBlobStore();
        PlayerBlobCacheState cache = new PlayerBlobCacheState(store, 1);
        field(f.player, "blobCacheState", cache);
        PlayerSubChunkRequestState flow = (PlayerSubChunkRequestState) field(f.player, "subChunkRequestState");
        flow.accountResponseBytes(10, flow.remainingResponseBytes(10) - 100);
        byte[] section = new byte[512];
        section[0] = 42;
        byte[] entities = new byte[128];
        entities[0] = 43;
        byte[] raw = new byte[section.length + entities.length];
        System.arraycopy(section, 0, raw, 0, section.length);
        System.arraycopy(entities, 0, raw, section.length, entities.length);
        SubChunkPacket.SubChunkData entry = new SubChunkPacket.SubChunkData();
        entry.offset = new BlockVector3(0, 0, 0);
        entry.result = SubChunkRequestResult.SUCCESS;
        entry.data = raw;
        long hash = XXHash64.hash(section);
        assertEquals(1, flow.acquireInFlightOffsets(1));
        flow.offerCompletion(new PlayerSubChunkRequestState.CompletedSubChunkAnswer(flow.epoch(), f.request(), true,
                List.of(entry), new long[]{hash}, new byte[][]{section}, new byte[][]{entities}, 1));
        PlayerHandle handle = new PlayerHandle(f.player);
        SubChunkRequestProcessor.tickRequests(handle);
        assertEquals(0, successfulEntries(f), "the cached form also exceeds this tick's remainder");
        assertTrue(flow.hasCompletions());
        cache.clear();
        assertTrue(cache.trackBlob(XXHash64.hash(new byte[]{1}), new byte[]{1}, 11));
        doReturn(11).when(f.server).getTick();

        SubChunkRequestProcessor.tickRequests(handle);

        SubChunkPacket packet = f.sent.stream().filter(SubChunkPacket.class::isInstance).map(SubChunkPacket.class::cast)
                .findFirst().orElseThrow();
        assertFalse(packet.cacheEnabled, "an expired pin cannot leave a cache-only payload in the retained entry");
        assertFalse(packet.subChunks.get(0).hasBlobId);
        assertArrayEquals(raw, packet.subChunks.get(0).data);
        assertEquals(0, flow.inFlightOffsetCount());
    }

    private static List<Runnable> captureSerialization(Fixture f) {
        f.server.asyncChunkSending = true;
        List<Runnable> workers = new ArrayList<>();
        java.util.concurrent.ExecutorService executor = mock(java.util.concurrent.ExecutorService.class);
        doAnswer(call -> { workers.add(call.getArgument(0)); return null; }).when(executor).execute(any(Runnable.class));
        doReturn(executor).when(f.level).getAsyncChuckExecutor();
        return workers;
    }

    private static long successfulEntries(Fixture f) {
        return f.sent.stream().filter(SubChunkPacket.class::isInstance).map(SubChunkPacket.class::cast)
                .flatMap(packet -> packet.subChunks.stream())
                .filter(entry -> entry.result == SubChunkRequestResult.SUCCESS).count();
    }

    private static final class Fixture {
        final Server server = mock(Server.class, CALLS_REAL_METHODS);
        final Level level = mock(Level.class, CALLS_REAL_METHODS);
        final Player player = mock(Player.class, CALLS_REAL_METHODS);
        final List<DataPacket> sent = new ArrayList<>();
        final Path file;
        final LevelDBChunk terrain;

        Fixture(Path directory, boolean cache) throws Exception {
            file = directory.resolve("nukkit-mot.yml");
            field(server, "dataPath", directory.toString());
            field(server, "properties", new Config(Config.PROPERTIES));
            reload(true, cache);
            field(level, "server", server);
            field(level, "dimensionData", DimensionEnum.OVERWORLD.getDimensionData());
            LevelDBProvider provider = mock(LevelDBProvider.class);
            when(provider.getLevel()).thenReturn(level);
            when(provider.getMinBlockY()).thenReturn(-64);
            when(provider.getMaxBlockY()).thenReturn(319);
            terrain = LevelDBChunk.getEmptyChunk(0, 0, provider);
            terrain.setBlock(0, 64, 0, BlockID.STONE);
            doReturn(terrain).when(level).getChunkIfLoaded(0, 0);
            doReturn(java.util.Map.of()).when(level).getChunkEntities(anyInt(), anyInt(), eq(false));
            doReturn(java.util.Map.of()).when(level).getChunkBlockEntities(anyInt(), anyInt());
            doNothing().when(level).unregisterChunkLoader(any(), anyInt(), anyInt());
            field(player, "server", server);
            field(player, "level", level);
            field(player, "connected", true);
            field(player, "usedChunks", new HashMap<>(java.util.Map.of(HASH, Boolean.FALSE)));
            field(player, "loadQueue", new LongLinkedOpenHashSet());
            field(player, "subChunkRequestState", new PlayerSubChunkRequestState());
            field(player, "gameVersion", VERSION);
            field(player, "protocol", VERSION.getProtocol());
            doAnswer(call -> {
                DataPacket packet = call.getArgument(0);
                packet.gameVersion = VERSION;
                packet.protocol = VERSION.getProtocol();
                packet.tryEncode();
                sent.add(packet);
                return true;
            }).when(player).dataPacket(any(DataPacket.class));
        }

        void useNativePacketEvents(java.util.function.Consumer<cn.nukkit.event.server.DataPacketSendEvent> action) throws Exception {
            cn.nukkit.network.session.NetworkPlayerSession network = mock(cn.nukkit.network.session.NetworkPlayerSession.class);
            cn.nukkit.plugin.PluginManager plugins = mock(cn.nukkit.plugin.PluginManager.class);
            field(player, "networkSession", network);
            server.callDataPkSendEv = true;
            doReturn(plugins).when(server).getPluginManager();
            doAnswer(call -> { sent.add(call.getArgument(0)); return null; })
                    .when(network).sendPacket(any(DataPacket.class));
            doAnswer(call -> {
                if (call.getArgument(0) instanceof cn.nukkit.event.server.DataPacketSendEvent event) action.accept(event);
                return null;
            }).when(plugins).callEvent(any(cn.nukkit.event.Event.class));
            doCallRealMethod().when(player).dataPacket(any(DataPacket.class));
        }

        void reload(boolean pull, boolean cache) throws Exception {
            reload(pull, cache, 1);
        }

        void reload(boolean pull, boolean cache, int entryBudget) throws Exception {
            Files.writeString(file, "chunk-settings:\n  sub-chunk-requests: " + pull
                    + "\n  client-cache: " + cache + "\n  subchunk-entries-per-tick: " + entryBudget + "\n");
            Method reload = Server.class.getDeclaredMethod("loadServerConfig", boolean.class);
            reload.setAccessible(true);
            reload.invoke(server, false);
            assertNotNull(server.getServerConfig(), "Native config hook must load actual file");
            assertEquals(pull, server.getServerConfig().chunkSettings().subChunkRequests());
            assertEquals(cache, server.getServerConfig().chunkSettings().clientCache());
        }

        void sendShell() throws Exception {
            AtomicReference<byte[]> biomes = new AtomicReference<>();
            java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger();
            NetworkChunkSerializer.serialize(new ObjectOpenHashSet<>(List.of(VERSION)), terrain.cloneForChunkSending(),
                    result -> { biomes.set(result.getBiomes()); count.set(result.getSubchunks()); },
                    false, level.getDimensionData(), true);
            assertNotNull(biomes.get());
            Method build = Level.class.getDeclaredMethod("buildPullLevelChunkPacket", GameVersion.class,
                    int.class, int.class, int.class, boolean.class, long.class, byte[].class);
            build.setAccessible(true);
            LevelChunkPacket shell = (LevelChunkPacket) build.invoke(level, VERSION, 0, 0, count.get(), false, 0L, biomes.get());
            assertTrue(shell.requestSubChunks);
            int sentBefore = sent.size();
            player.sendChunk(0, 0, shell);
            assertTrue(sent.subList(sentBefore, sent.size()).stream().anyMatch(packet -> packet instanceof LevelChunkPacket chunk
                            && chunk.requestSubChunks && chunk.chunkX == 0 && chunk.chunkZ == 0),
                    "Native shell must reach accepted transport boundary, including native packet clones");
        }

        SubChunkRequestPacket request() {
            SubChunkRequestPacket request = new SubChunkRequestPacket();
            request.dimension = 0;
            request.subChunkPosition = new BlockVector3(0, 4, 0);
            return request;
        }
    }

    private static Object field(Object target, String name) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(target); }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    private static void field(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value); return; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
}
