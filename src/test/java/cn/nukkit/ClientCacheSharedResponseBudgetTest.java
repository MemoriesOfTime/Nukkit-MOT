package cn.nukkit;

import cn.nukkit.event.Event;
import cn.nukkit.event.server.DataPacketSendEvent;
import cn.nukkit.level.ClientCacheBlobStore;
import cn.nukkit.level.PlayerBlobCacheState;
import cn.nukkit.level.PlayerSubChunkRequestState;
import cn.nukkit.network.process.processor.common.ClientCacheBlobStatusProcessor;
import cn.nukkit.network.protocol.ClientCacheBlobStatusPacket;
import cn.nukkit.network.protocol.ClientCacheMissResponsePacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.session.NetworkPlayerSession;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.utils.XXHash64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Real handler, byte ledger and store; only transport and Server shell are mocked. */
class ClientCacheSharedResponseBudgetTest {

    /** 单条 miss 条目的固定开销，直接取自生产记账公式而非写死。 */
    private static final int ENTRY_OVERHEAD = ClientCacheBlobStatusProcessor.missEntryBytes(0);

    @Test
    void secondSameTickMissDefersAfterBudgetExhaustedThenDrainsNextTick() throws Exception {
        Fixture f = new Fixture();
        int budget = f.flow.remainingResponseBytes(f.tick.get());
        long[] first = new long[4];
        for (int i = 0; i < first.length; i++) {
            // 四条恰好铺满一个 tick 的预算，故本轮发送字节数必须精确等于 budget。
            first[i] = f.blobWithEntryBytes(budget / first.length);
        }
        f.miss(first);
        assertEquals(budget, f.sentBytes());
        assertEquals(0, f.flow.remainingResponseBytes(f.tick.get()));
        assertEquals(0, f.cache.getPendingSize(), "first response already resolved all pending hashes");
        int sent = f.sent.size();

        f.miss(first[0]); // Replay still-resident store hash after its pending transaction resolved.

        assertAll("repeated BlobStatus packet must share the exhausted tick ledger",
                () -> assertEquals(sent, f.sent.size(), "no additional packet may bypass exhausted budget"),
                () -> assertEquals(1, f.flow.deferredMissHashCount(), "second same-tick miss must wait"));
        ClientCacheBlobStatusProcessor.drainDeferredMissResponses(f.handle);
        assertEquals(sent, f.sent.size(), "same-tick drain must not force another first entry");
        assertEquals(1, f.flow.deferredMissHashCount());
        f.tick.incrementAndGet();
        ClientCacheBlobStatusProcessor.drainDeferredMissResponses(f.handle);
        assertEquals(sent + 1, f.sent.size());
        assertEquals(0, f.flow.deferredMissHashCount());
    }

    @Test
    void priorResponseWorkMustNotForceOversizedFirstEntry() throws Exception {
        Fixture f = new Fixture();
        int budget = f.flow.remainingResponseBytes(f.tick.get());
        long first = f.blobWithEntryBytes(budget / 2);
        long tooLargeForRemaining = f.blobWithEntryBytes(budget / 2 + 1024);
        f.miss(first);
        int sent = f.sent.size();
        assertEquals(budget / 2, f.flow.remainingResponseBytes(f.tick.get()));

        f.miss(tooLargeForRemaining);

        assertEquals(sent, f.sent.size(),
                "first-entry starvation exception belongs only to a tick with no prior response work");
        assertEquals(1, f.flow.deferredMissHashCount());
        assertEquals(1, f.cache.getPendingSize(), "deferred legitimate hash must keep its pin/pending owner");
        f.tick.incrementAndGet();
        ClientCacheBlobStatusProcessor.drainDeferredMissResponses(f.handle);
        assertEquals(sent + 1, f.sent.size());
        assertEquals(0, f.flow.deferredMissHashCount());
        assertEquals(0, f.cache.getPendingSize());
        assertEquals(0, f.store.pinnedByteSize());
    }

    @Test
    void oversizedFirstEntryMayProgressOnlyOncePerFreshTick() throws Exception {
        Fixture f = new Fixture();
        int budget = f.flow.remainingResponseBytes(f.tick.get());
        long huge = f.blobWithEntryBytes(budget + 1024);
        f.miss(huge);
        assertEquals(1, f.sent.size(), "single oversized first entry must make progress on a fresh tick");
        assertTrue(f.flow.responseBytesExceeded(f.tick.get()));

        f.miss(huge);

        assertEquals(1, f.sent.size(), "starvation exception must not reset for every incoming packet");
        assertEquals(1, f.flow.deferredMissHashCount());
        f.tick.incrementAndGet();
        ClientCacheBlobStatusProcessor.drainDeferredMissResponses(f.handle);
        assertEquals(2, f.sent.size(), "next fresh tick may serve its oversized first entry");
        assertEquals(0, f.flow.deferredMissHashCount());
    }

    @Test
    void missHonorsBytesAlreadySpentBySubChunkResponses() throws Exception {
        Fixture f = new Fixture();
        int budget = f.flow.remainingResponseBytes(f.tick.get());
        f.flow.accountResponseBytes(f.tick.get(), budget); // Same real ledger used by SubChunkRequestProcessor.
        long hash = f.blobWithEntryBytes(128);

        f.miss(hash);

        assertTrue(f.sent.isEmpty(), "sub-chunk response bytes must gate immediate blob misses too");
        assertEquals(1, f.flow.deferredMissHashCount());
        assertEquals(1, f.cache.getPendingSize());
        f.tick.incrementAndGet();
        ClientCacheBlobStatusProcessor.drainDeferredMissResponses(f.handle);
        assertEquals(1, f.sent.size());
        assertEquals(0, f.cache.getPendingSize());
    }

    @Test
    void boundedNestedMissCallbackCannotReserveTheSameTickBudgetTwice() throws Exception {
        Fixture f = new Fixture();
        int budget = f.flow.remainingResponseBytes(f.tick.get());
        long hash = f.blobWithEntryBytes(budget);
        AtomicInteger callbacks = new AtomicInteger();
        f.server.callDataPkSendEv = true;
        doAnswer(call -> {
            Event event = call.getArgument(0);
            if (event instanceof DataPacketSendEvent send
                    && send.getPacket() instanceof ClientCacheMissResponsePacket
                    && callbacks.getAndIncrement() == 0) f.miss(hash);
            return null;
        }).when(f.plugins).callEvent(any(Event.class));

        f.miss(hash);

        assertEquals(1, callbacks.get(), "only outer response may enter packet-send callback this tick");
        assertEquals(1, f.sent.size(), "nested same-tick miss must not reuse outer response budget");
        assertEquals(1, f.flow.deferredMissHashCount());
        assertEquals(0, f.flow.remainingResponseBytes(f.tick.get()));
        f.tick.incrementAndGet();
        ClientCacheBlobStatusProcessor.drainDeferredMissResponses(f.handle);
        assertEquals(2, f.sent.size(), "nested request resumes in next tick");
        assertEquals(0, f.flow.deferredMissHashCount());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectedOrThrowingResponseRetainsWorkReservation(boolean throwsListener) throws Exception {
        Fixture f = new Fixture();
        int budget = f.flow.remainingResponseBytes(f.tick.get());
        long hash = f.blobWithEntryBytes(budget);
        IllegalStateException expected = new IllegalStateException("controlled miss-response listener failure");
        AtomicInteger callbacks = new AtomicInteger();
        f.server.callDataPkSendEv = true;
        doAnswer(call -> {
            Event event = call.getArgument(0);
            if (event instanceof DataPacketSendEvent send
                    && send.getPacket() instanceof ClientCacheMissResponsePacket
                    && callbacks.getAndIncrement() == 0) {
                if (throwsListener) throw expected;
                send.setCancelled();
            }
            return null;
        }).when(f.plugins).callEvent(any(Event.class));

        if (throwsListener) assertSame(expected, assertThrows(IllegalStateException.class, () -> f.miss(hash)));
        else f.miss(hash);

        assertTrue(f.sent.isEmpty());
        assertEquals(0, f.flow.remainingResponseBytes(f.tick.get()),
                "serialized response work remains reserved even if transport callback rejects or throws");
        f.miss(hash);
        assertTrue(f.sent.isEmpty(), "rejected work must not grant a second reservation in same tick");
        assertEquals(1, f.flow.deferredMissHashCount());
        f.tick.incrementAndGet();
        ClientCacheBlobStatusProcessor.drainDeferredMissResponses(f.handle);
        assertEquals(1, f.sent.size());
    }

    @Test
    void resetInsideMissCallbackCannotResolveNewPinsOrRestoreOldResidual() throws Exception {
        Fixture f = new Fixture();
        int budget = f.flow.remainingResponseBytes(f.tick.get());
        long sentHash = f.blobWithEntryBytes(budget / 2);
        long deferredHash = f.blobWithEntryBytes(budget);
        f.server.callDataPkSendEv = true;
        doAnswer(call -> {
            if (call.getArgument(0) instanceof DataPacketSendEvent send
                    && send.getPacket() instanceof ClientCacheMissResponsePacket) {
                f.flow.clear();
                f.cache.clear();
                assertTrue(f.cache.tryOpenTransaction(new long[]{sentHash}, f.tick.get()));
            }
            return null;
        }).when(f.plugins).callEvent(any(Event.class));

        f.miss(sentHash, deferredHash);

        assertEquals(1, f.cache.getPendingSize(), "the old response must not resolve a new epoch's pin for the same hash");
        assertEquals(1, f.cache.getOpenTransactionCount());
        assertEquals(0, f.flow.deferredMissHashCount(), "the old residual must not repopulate the cleared backlog");
        assertEquals(budget, f.flow.remainingResponseBytes(f.tick.get()));
    }

    private static final class Fixture {
        final AtomicInteger tick = new AtomicInteger(100);
        final Server server = mock(Server.class);
        final PluginManager plugins = mock(PluginManager.class);
        final ClientCacheBlobStore store = new ClientCacheBlobStore(64L << 20);
        final PlayerBlobCacheState cache = new PlayerBlobCacheState(store, 512);
        final PlayerSubChunkRequestState flow = new PlayerSubChunkRequestState();
        final Player player = mock(Player.class, CALLS_REAL_METHODS);
        final PlayerHandle handle = new PlayerHandle(player);
        final List<ClientCacheMissResponsePacket> sent = new ArrayList<>();
        int unique;

        Fixture() throws Exception {
            NetworkPlayerSession network = mock(NetworkPlayerSession.class);
            field(player, "server", server);
            field(player, "networkSession", network);
            field(player, "gameVersion", GameVersion.V1_21_70);
            field(player, "subChunkRequestState", flow);
            player.blobCacheState = cache;
            player.connected = true;
            player.protocol = GameVersion.V1_21_70.getProtocol();
            when(server.getPluginManager()).thenReturn(plugins);
            when(server.getClientCacheBlobStore()).thenReturn(store);
            when(server.getTick()).thenAnswer(call -> tick.get());
            doAnswer(call -> {
                DataPacket packet = call.getArgument(0);
                sent.add(assertInstanceOf(ClientCacheMissResponsePacket.class, packet));
                return null;
            }).when(network).sendPacket(any(DataPacket.class));
        }

        long blobWithEntryBytes(int wireBytes) {
            // 让 fixture 与生产记账使用同一套开销公式，避免把 entry 头长度写死。
            byte[] payload = new byte[wireBytes - ENTRY_OVERHEAD];
            payload[0] = (byte) ++unique;
            long hash = XXHash64.hash(payload);
            store.put(hash, payload);
            assertTrue(cache.tryOpenTransaction(new long[]{hash}, tick.get()));
            return hash;
        }

        void miss(long... hashes) {
            ClientCacheBlobStatusPacket packet = new ClientCacheBlobStatusPacket();
            packet.missHashes = hashes;
            ClientCacheBlobStatusProcessor.INSTANCE.handle(handle, packet);
        }

        long sentBytes() {
            long bytes = 0;
            for (ClientCacheMissResponsePacket packet : sent) {
                for (byte[] payload : packet.payloads) bytes += ClientCacheBlobStatusProcessor.missEntryBytes(payload.length);
            }
            return bytes;
        }
    }

    private static void field(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
}
