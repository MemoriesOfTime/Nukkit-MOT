package cn.nukkit.level;

import cn.nukkit.utils.XXHash64;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PlayerBlobCacheState 事务状态机：开事务/pending 上限/resolve 关闭/超时强关/清理。
 * <p>
 * PlayerBlobCacheState transaction state machine: open / pending cap / resolve close /
 * timeout force-close / clear.
 */
public class PlayerBlobCacheStateTest {

    private final ClientCacheBlobStore store = new ClientCacheBlobStore(1 << 20);

    private static long hash(byte fill) {
        byte[] data = new byte[16];
        java.util.Arrays.fill(data, fill);
        return XXHash64.hash(data);
    }

    @Test
    public void openResolveAndClose() {
        PlayerBlobCacheState state = new PlayerBlobCacheState(store, 8);
        long h1 = hash((byte) 1);
        long h2 = hash((byte) 2);
        long h3 = hash((byte) 3);

        assertTrue(state.tryOpenTransaction(new long[]{h1, h2}, 0));
        assertEquals(1, state.getOpenTransactionCount());
        assertEquals(2, state.getPendingSize());
        assertTrue(store.isPinned(h1) && store.isPinned(h2));

        state.resolve(h1); // 事务未清空
        assertEquals(1, state.getOpenTransactionCount());
        assertEquals(1, state.getPendingSize());
        assertFalse(store.isPinned(h1));

        state.resolve(h2); // 事务清空即关闭
        assertEquals(0, state.getOpenTransactionCount());
        assertEquals(0, state.getPendingSize());
        assertFalse(store.isPinned(h2));

        state.resolve(h3); // 未知哈希 resolve 为 no-op
        assertEquals(0, state.getPendingSize());
    }

    @Test
    public void pendingCapRejects() {
        PlayerBlobCacheState state = new PlayerBlobCacheState(store, 3);
        assertTrue(state.tryOpenTransaction(new long[]{hash((byte) 1), hash((byte) 2)}, 0));
        assertFalse(state.tryOpenTransaction(new long[]{hash((byte) 3), hash((byte) 4)}, 0), "cap 3 must reject 4th pending hash");
        assertEquals(1, state.getOpenTransactionCount());
        assertTrue(state.tryOpenTransaction(new long[]{hash((byte) 3)}, 0), "exactly at cap must pass");
    }

    @Test
    public void pendingCapCountsOnlyHashesNewToPending() {
        PlayerBlobCacheState state = new PlayerBlobCacheState(store, 3);
        long shared = hash((byte) 9);
        long a = hash((byte) 1);
        long b = hash((byte) 2);
        assertTrue(state.tryOpenTransaction(new long[]{shared, a}, 0));
        // pending={shared,a}：新事务只新增 b（shared 已 pending 不重复占额），2+1=3 恰达
        // 上限不得拒绝；旧行为按 2+2=4 误判超限，大视野首屏因此整体降级全量
        // / pending={shared,a}: the new transaction adds only b (shared must not
        // double-charge), 2+1=3 is exactly at the cap and must pass; the old check
        // counted 2+2=4 and wrongly degraded large first screens to full sends
        assertTrue(state.tryOpenTransaction(new long[]{shared, b}, 0), "cap must count only hashes new to pending");
        assertFalse(state.tryOpenTransaction(new long[]{hash((byte) 3)}, 0), "a genuinely new 4th hash must still be rejected");
        assertEquals(3, state.getPendingSize());
    }

    @Test
    public void sharedHashAcrossTransactionsResolvesAll() {
        PlayerBlobCacheState state = new PlayerBlobCacheState(store, 8);
        long shared = hash((byte) 9);
        long h1 = hash((byte) 1);
        long h2 = hash((byte) 2);
        assertTrue(state.tryOpenTransaction(new long[]{shared, h1}, 0));
        assertTrue(state.tryOpenTransaction(new long[]{shared, h2}, 0));
        assertTrue(store.isPinned(shared), "two transactions must hold two pin refs");

        state.resolve(shared); // 从所有事务移除并解除全部 pin，两个事务各自剩一个哈希
        assertEquals(2, state.getOpenTransactionCount(), "each transaction keeps its other hash");
        assertFalse(store.isPinned(shared), "both pin refs must be released");
        assertEquals(2, state.getPendingSize());

        state.resolve(h1);
        state.resolve(h2);
        assertEquals(0, state.getOpenTransactionCount());
        assertEquals(0, state.getPendingSize());
    }

    @Test
    public void closeExpiredForceResolves() {
        PlayerBlobCacheState state = new PlayerBlobCacheState(store, 8);
        long h1 = hash((byte) 1);
        assertTrue(state.tryOpenTransaction(new long[]{h1}, 0));

        assertEquals(0, state.closeExpired(50, 100), "not yet expired");
        assertEquals(1, state.closeExpired(101, 100), "expired at 101");
        assertEquals(0, state.getOpenTransactionCount());
        assertEquals(0, state.getPendingSize());
        assertFalse(store.isPinned(h1));
    }

    @Test
    public void closeExpiredKeepsPendingWhileAnotherTransactionHoldsTheHash() {
        PlayerBlobCacheState state = new PlayerBlobCacheState(store, 8);
        long shared = hash((byte) 7);
        store.put(shared, new byte[16]);
        assertTrue(state.tryOpenTransaction(new long[]{shared}, 0));   // pin 引用 1 / pin ref 1
        assertTrue(state.tryOpenTransaction(new long[]{shared}, 50));  // pin 引用 2 / pin ref 2

        assertEquals(1, state.closeExpired(101, 100), "only the older transaction expires");
        assertEquals(1, state.getPendingSize(), "shared hash stays pending while the later transaction holds it");
        assertEquals(16, store.pinnedByteSize(), "one pin ref of the live transaction remains");

        // 事务仍持有时 trackBlob 不得重复记账：旧行为（超时方无条件清 pending）在此叠出
        // 第二个 pin，随后 resolve 因 inAnyTransaction 跳过解钉，泄漏连 clear() 都不放的永久 pin
        // / While a transaction still holds the hash, trackBlob must not re-account:
        // the old unconditional pending removal stacked a second pin here and the
        // following resolve skipped the unpin (inAnyTransaction), leaking a permanent
        // pin that not even clear() releases
        assertTrue(state.trackBlob(shared, new byte[16], 101));
        assertEquals(16, store.pinnedByteSize());

        state.resolve(shared);
        assertFalse(store.isPinned(shared), "the last holder's resolve must fully release the pin");
        assertEquals(0, store.pinnedByteSize());
        assertEquals(0, state.getPendingSize());
    }

    @Test
    public void clearUnpinsEverything() {
        PlayerBlobCacheState state = new PlayerBlobCacheState(store, 8);
        long h1 = hash((byte) 1);
        long h2 = hash((byte) 2);
        assertTrue(state.tryOpenTransaction(new long[]{h1, h2}, 0));
        state.clear();
        assertEquals(0, state.getOpenTransactionCount());
        assertEquals(0, state.getPendingSize());
        assertFalse(store.isPinned(h1));
        assertFalse(store.isPinned(h2));
    }

    @Test
    public void trackBlobWithoutTransaction() {
        PlayerBlobCacheState state = new PlayerBlobCacheState(store, 1);
        byte[] payload = new byte[16];
        long h = XXHash64.hash(payload);
        assertTrue(state.trackBlob(h, payload, 0));
        assertEquals(1, state.getPendingSize());
        assertTrue(store.isPinned(h));
        assertArrayEquals(payload, store.get(h));
        assertFalse(state.trackBlob(XXHash64.hash(new byte[17]), new byte[17], 0), "cap 1 rejects a second pending blob");

        state.resolve(h);
        assertEquals(0, state.getPendingSize());
        assertFalse(store.isPinned(h));
    }

    @Test
    public void trackBlobPinTimesOut() {
        PlayerBlobCacheState state = new PlayerBlobCacheState(store, 8);
        byte[] payload = new byte[16];
        long h = XXHash64.hash(payload);
        assertTrue(state.trackBlob(h, payload, 0));
        assertTrue(store.isPinned(h));

        assertEquals(0, state.closeExpired(100, 100), "not yet expired");
        assertTrue(store.isPinned(h), "before timeout the standalone pin holds");
        assertEquals(1, state.closeExpired(101, 100), "expired at 101");
        assertFalse(store.isPinned(h), "timeout sweep must release the standalone pin");
        assertEquals(0, state.getPendingSize());
        assertEquals(0, state.closeExpired(150, 100), "sweep is not repeated");
    }

    @Test
    public void trackBlobRetrackRefreshesDeadline() {
        PlayerBlobCacheState state = new PlayerBlobCacheState(store, 8);
        byte[] payload = new byte[16];
        long h = XXHash64.hash(payload);
        assertTrue(state.trackBlob(h, payload, 0));
        assertTrue(state.trackBlob(h, payload, 50), "re-track of a pending hash is a no-op success");

        assertEquals(0, state.closeExpired(101, 100), "re-track refreshed the deadline (101 - 50 <= 100)");
        assertTrue(store.isPinned(h));
        assertEquals(1, state.closeExpired(151, 100), "151 - 50 > 100 expires");
        assertFalse(store.isPinned(h));
        assertEquals(0, state.getPendingSize());
    }

    @Test
    public void trackOfTransactionPendingHashHasNoStandaloneSweep() {
        PlayerBlobCacheState state = new PlayerBlobCacheState(store, 8);
        long h = hash((byte) 5);
        assertTrue(state.tryOpenTransaction(new long[]{h}, 0));
        assertTrue(state.trackBlob(h, new byte[16], 0), "already pending: no-op success");

        assertEquals(1, state.closeExpired(101, 100), "only the transaction closes");
        assertFalse(store.isPinned(h), "no phantom standalone pin survives the transaction");
        assertEquals(0, state.getPendingSize());
    }

    @Test
    public void standalonePinAdoptedByTransactionReleasesOnTimeout() {
        PlayerBlobCacheState state = new PlayerBlobCacheState(store, 8);
        byte[] payload = new byte[16];
        long h = XXHash64.hash(payload);
        assertTrue(state.trackBlob(h, payload, 0));
        assertTrue(state.tryOpenTransaction(new long[]{h}, 0));

        assertEquals(1, state.closeExpired(101, 100));
        assertFalse(store.isPinned(h), "adopted pin releases with the transaction timeout");
        assertEquals(0, state.getPendingSize());
        assertEquals(0, state.closeExpired(150, 100), "adoption removed the standalone sweep entry");
    }

    @Test
    public void clearReleasesStandalonePin() {
        PlayerBlobCacheState state = new PlayerBlobCacheState(store, 8);
        byte[] payload = new byte[16];
        long h = XXHash64.hash(payload);
        assertTrue(state.trackBlob(h, payload, 0));
        state.clear();
        assertFalse(store.isPinned(h));
        assertEquals(0, state.getPendingSize());
        assertEquals(0, state.closeExpired(101, 100), "clear removes the sweep entry");
    }

    @Test
    public void duplicateHashesPinOncePerDistinctHash() {
        PlayerBlobCacheState state = new PlayerBlobCacheState(store, 8);
        long h = hash((byte) 5);
        // 空子区块同内容重复：4 个负坐标占位 + 1 个空 section 同哈希进同一事务
        // / Repeated empty content: 4 negative placeholders + 1 empty section share one hash
        assertTrue(state.tryOpenTransaction(new long[]{h, h, h, h, h}, 0));
        assertEquals(1, state.getPendingSize(), "duplicates collapse in pending");
        assertTrue(store.isPinned(h));

        state.resolve(h);
        assertFalse(store.isPinned(h), "one pin per distinct hash must fully release on resolve");
        assertEquals(0, state.getPendingSize());
    }

    @Test
    public void duplicateHashesClearAndTimeoutRelease() {
        long h = hash((byte) 5);

        PlayerBlobCacheState cleared = new PlayerBlobCacheState(store, 8);
        assertTrue(cleared.tryOpenTransaction(new long[]{h, h, h}, 0));
        cleared.clear();
        assertFalse(store.isPinned(h), "clear must release every pin of a duplicated hash");
        assertEquals(0, cleared.getPendingSize());

        PlayerBlobCacheState expired = new PlayerBlobCacheState(store, 8);
        assertTrue(expired.tryOpenTransaction(new long[]{h, h, h}, 0));
        assertEquals(1, expired.closeExpired(101, 100));
        assertFalse(store.isPinned(h), "timeout close must release every pin of a duplicated hash");
        assertEquals(0, expired.getPendingSize());
    }

    @Test
    public void duplicateHashesDoNotConsumeExtraPendingCap() {
        PlayerBlobCacheState state = new PlayerBlobCacheState(store, 2);
        long h = hash((byte) 5);
        long other = hash((byte) 6);
        // 3 份重复只占 1 个 pending 位，再加 1 个不同哈希仍在界内
        // / Three duplicates occupy one pending slot; one more distinct hash still fits
        assertTrue(state.tryOpenTransaction(new long[]{h, h, h, other}, 0));
        assertEquals(2, state.getPendingSize());
    }

    @Test
    public void transactionReusesStandaloneTrackBlobPin() {
        PlayerBlobCacheState state = new PlayerBlobCacheState(store, 8);
        byte[] payload = new byte[16];
        long h = XXHash64.hash(payload);
        assertTrue(state.trackBlob(h, payload, 0));

        // 模式切换后同一哈希进事务：复用独立 pin 而非叠加
        // / The same hash joins a transaction after a mode switch: reuse, not stack
        assertTrue(state.tryOpenTransaction(new long[]{h}, 0));
        state.resolve(h);
        assertFalse(store.isPinned(h), "transferred standalone pin must release with the transaction");
        assertEquals(0, state.getPendingSize());
    }

    @Test
    public void pinnedByteCapRejectsWholeTransaction() {
        ClientCacheBlobStore tiny = new ClientCacheBlobStore(20);
        PlayerBlobCacheState state = new PlayerBlobCacheState(tiny, 8);
        byte[] first = new byte[16];
        byte[] second = new byte[16];
        first[0] = 1;
        second[0] = 2;
        long h1 = XXHash64.hash(first);
        long h2 = XXHash64.hash(second);
        tiny.put(h1, first);
        assertTrue(state.tryOpenTransaction(new long[]{h1}, 0), "16 pinned fits budget 20");
        // 首块 pin 后再入库第二块：超预算驱逐无可驱逐者，内容都在 / with the first blob
        // pinned, the over-budget put has nothing evictable left; both stay
        tiny.put(h2, second);
        assertFalse(state.tryOpenTransaction(new long[]{h2}, 0), "16 + 16 > 20 must reject");
        // 整体降级：不产生半开事务、不 pending、不 pin / degrades whole: no half-open
        // transaction, no pending entry, no pin
        assertEquals(1, state.getOpenTransactionCount());
        assertEquals(1, state.getPendingSize());
        assertFalse(tiny.isPinned(h2));
        assertArrayEquals(second, tiny.get(h2), "the rejected blob stays as unpinned LRU content");
    }

    @Test
    public void trackBlobDegradesWhenPinnedBytesFull() {
        ClientCacheBlobStore tiny = new ClientCacheBlobStore(20);
        PlayerBlobCacheState state = new PlayerBlobCacheState(tiny, 8);
        byte[] first = new byte[16];
        byte[] second = new byte[16];
        first[0] = 1;
        second[0] = 2;
        long h1 = XXHash64.hash(first);
        long h2 = XXHash64.hash(second);

        assertTrue(state.trackBlob(h1, first, 0));
        assertFalse(state.trackBlob(h2, second, 0), "16 pinned + 16 new > 20 must degrade");

        // 降级后无 pending、无 pin，blob 以未 pin 身份留在仓库 / after the degradation:
        // no pending, no pin; the blob stays in the store unpinned
        assertEquals(1, state.getPendingSize());
        assertFalse(tiny.isPinned(h2));
        assertArrayEquals(second, tiny.get(h2));

        // 独立 pin 释放后恢复余量 / releasing the standalone pin restores headroom
        state.resolve(h1);
        assertTrue(state.trackBlob(h2, second, 1));
        assertTrue(tiny.isPinned(h2));
    }

    @Test
    public void pinnedByteCapStillBoundsNeverAnsweringClients() {
        // 模拟不回 BlobStatus 的客户端：pinned 字节停在硬顶，仓库总字节不超顶太多
        // / Simulates a client that never answers BlobStatus: pinned bytes stall at the
        // hard cap and the store's total stays within the cap plus epsilon
        ClientCacheBlobStore tiny = new ClientCacheBlobStore(64);
        PlayerBlobCacheState state = new PlayerBlobCacheState(tiny, 1000);
        int tracked = 0;
        for (int i = 0; i < 100; i++) {
            byte[] payload = new byte[2];
            payload[0] = (byte) i;
            if (state.trackBlob(XXHash64.hash(payload), payload, 0)) {
                tracked++;
            }
        }
        assertTrue(tracked < 100, "the pinned cap must reject before the pending cap (1000)");
        assertEquals(32, tracked, "64-byte cap holds 32 two-byte blobs");
        assertTrue(tiny.pinnedByteSize() <= 64, "pinned bytes must respect the hard cap");
        assertTrue(tiny.byteSize() <= 64 + 2, "total store bytes stay at cap + one blob");
    }
}
