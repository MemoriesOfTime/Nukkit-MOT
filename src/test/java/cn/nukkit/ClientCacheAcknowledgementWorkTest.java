package cn.nukkit;

import cn.nukkit.level.ClientCacheBlobStore;
import cn.nukkit.level.PlayerBlobCacheState;
import cn.nukkit.network.process.processor.common.ClientCacheBlobStatusProcessor;
import cn.nukkit.network.protocol.ClientCacheBlobStatusPacket;
import cn.nukkit.utils.XXHash64;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Counts native state work; transaction contents are created only through public admission. */
class ClientCacheAcknowledgementWorkTest {
    private static final int TRANSACTIONS = 1024;
    private static final int HASHES_PER_PACKET = 4095;

    @Test
    void unknownHitsDoNotTraverseTransactionsEvenWhenPendingCapIsOne() throws Exception {
        Fixture f = new Fixture(1);
        long shared = f.put(new byte[]{1, 2, 3, 4});
        f.openRepeated(TRANSACTIONS, shared);
        assertEquals(1, f.state.getPendingSize(), "duplicate content occupies one pending slot");
        assertEquals(TRANSACTIONS, f.state.getOpenTransactionCount(),
                "unique-hash cap does not cap number of admitted real chunk transactions");
        long unknown = shared ^ Long.MIN_VALUE;
        assertFalse(f.store.contains(unknown));

        f.handleHits(unknown, HASHES_PER_PACKET);

        assertNoTransactionWalk(f, "unknown hits");
        assertEquals(TRANSACTIONS, f.state.getOpenTransactionCount());
        assertEquals(1, f.state.getPendingSize());
        assertEquals(4, f.store.pinnedByteSize());
    }

    @Test
    void repeatedResolvedHitsDoNotTraverseUnrelatedLiveTransactions() throws Exception {
        Fixture f = new Fixture(2);
        long resolved = f.put(new byte[]{11, 12, 13});
        long live = f.put(new byte[]{21, 22, 23});
        f.openRepeated(TRANSACTIONS, resolved, resolved, live);
        f.state.resolve(resolved);
        assertEquals(3, f.store.pinnedByteSize(), "resolved hash releases all refs, unrelated live payload remains pinned");
        assertEquals(TRANSACTIONS, f.state.getOpenTransactionCount());
        assertEquals(1, f.state.getPendingSize());
        f.work.reset();

        f.handleHits(resolved, HASHES_PER_PACKET);

        assertNoTransactionWalk(f, "duplicate hits for an already resolved hash");
        assertEquals(TRANSACTIONS, f.state.getOpenTransactionCount());
        f.state.resolve(live);
        assertEquals(0, f.state.getOpenTransactionCount());
        assertEquals(0, f.state.getPendingSize());
        assertEquals(0, f.store.pinnedByteSize());
    }

    @Test
    void unknownMissesDoNotTraverseTransactions() throws Exception {
        Fixture f = new Fixture(1);
        long live = f.put(new byte[]{31, 32, 33});
        f.openRepeated(TRANSACTIONS, live);
        long unknown = live ^ Long.MIN_VALUE;
        ClientCacheBlobStatusPacket packet = new ClientCacheBlobStatusPacket();
        packet.missHashes = repeated(unknown, HASHES_PER_PACKET);

        ClientCacheBlobStatusProcessor.INSTANCE.handle(new PlayerHandle(f.player), packet);

        assertNoTransactionWalk(f, "unknown misses with no store payload");
        assertEquals(TRANSACTIONS, f.state.getOpenTransactionCount());
        assertEquals(3, f.store.pinnedByteSize());
    }

    @Test
    void legitimateHitsStillReleaseAllTransactionsAndPins() throws Exception {
        int count = 256;
        Fixture f = new Fixture(count);
        long[] hashes = new long[count];
        for (int i = 0; i < count; i++) {
            hashes[i] = f.put(ByteBuffer.allocate(4).putInt(i).array());
            assertTrue(f.state.tryOpenTransaction(new long[]{hashes[i]}, 0));
        }
        assertEquals(count, f.state.getPendingSize());
        f.work.reset();
        ClientCacheBlobStatusPacket packet = new ClientCacheBlobStatusPacket();
        packet.hitHashes = hashes;

        ClientCacheBlobStatusProcessor.INSTANCE.handle(new PlayerHandle(f.player), packet);

        assertEquals(0, f.state.getPendingSize());
        assertEquals(0, f.state.getOpenTransactionCount());
        assertEquals(0, f.store.pinnedByteSize());
    }

    private static void assertNoTransactionWalk(Fixture f, String context) {
        long unguardedVisits = (long) TRANSACTIONS * HASHES_PER_PACKET;
        assertAll(context + "; unguarded H*T=" + unguardedVisits,
                () -> assertEquals(0, f.work.iteratorVisits, "non-pending hash must be O(1), not a transaction traversal"),
                () -> assertEquals(0, f.work.cleanupChecks, "non-pending hash must not scan all transactions for empty entries"));
    }

    private static long[] repeated(long hash, int count) {
        long[] result = new long[count];
        Arrays.fill(result, hash);
        return result;
    }

    private static final class Fixture {
        final ClientCacheBlobStore store = new ClientCacheBlobStore(1 << 20);
        final PlayerBlobCacheState state;
        final CountingDeque<Object> work = new CountingDeque<>();
        final Player player = mock(Player.class);

        Fixture(int cap) throws Exception {
            state = new PlayerBlobCacheState(store, cap);
            Field transactions = PlayerBlobCacheState.class.getDeclaredField("transactions");
            transactions.setAccessible(true);
            transactions.set(state, work);
            // This state exists natively only after client-cache config + negotiation activate it.
            // Handler/state are real; IO and Player shell are the only mocks.
            player.blobCacheState = state;
            Server server = mock(Server.class);
            when(player.getServer()).thenReturn(server);
            when(server.getClientCacheBlobStore()).thenReturn(store);
        }

        long put(byte[] payload) {
            long hash = XXHash64.hash(payload);
            store.put(hash, payload);
            return hash;
        }

        void openRepeated(int count, long... hashes) {
            for (int i = 0; i < count; i++) assertTrue(state.tryOpenTransaction(hashes, 0));
            work.reset();
        }

        void handleHits(long hash, int count) {
            ClientCacheBlobStatusPacket packet = new ClientCacheBlobStatusPacket();
            packet.hitHashes = repeated(hash, count);
            ClientCacheBlobStatusProcessor.INSTANCE.handle(new PlayerHandle(player), packet);
        }
    }

    /** Same ArrayDeque behavior, only visits counted; no manufactured transaction contents. */
    private static final class CountingDeque<E> extends ArrayDeque<E> {
        long iteratorVisits;
        long cleanupChecks;

        @Override
        public Iterator<E> iterator() {
            Iterator<E> original = super.iterator();
            return new Iterator<>() {
                public boolean hasNext() { return original.hasNext(); }
                public E next() { iteratorVisits++; return original.next(); }
                public void remove() { original.remove(); }
            };
        }

        @Override
        public boolean removeIf(Predicate<? super E> filter) {
            Objects.requireNonNull(filter);
            return super.removeIf(element -> { cleanupChecks++; return filter.test(element); });
        }

        void reset() {
            iteratorVisits = 0;
            cleanupChecks = 0;
        }
    }
}
