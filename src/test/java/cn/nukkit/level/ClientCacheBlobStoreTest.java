package cn.nukkit.level;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ClientCacheBlobStore 的 put/pin/LRU 驱逐与引用计数语义。
 * <p>
 * ClientCacheBlobStore put/pin/LRU eviction and refcount semantics.
 */
public class ClientCacheBlobStoreTest {

    private static byte[] bytes(int size, byte fill) {
        byte[] data = new byte[size];
        java.util.Arrays.fill(data, fill);
        return data;
    }

    @Test
    public void putGetContains() {
        ClientCacheBlobStore store = new ClientCacheBlobStore(1 << 20);
        byte[] a = bytes(10, (byte) 1);
        long h = cn.nukkit.utils.XXHash64.hash(a);
        store.put(h, a);
        assertTrue(store.contains(h));
        assertArrayEquals(a, store.get(h));
        assertNull(store.get(h + 1));
        assertEquals(10, store.byteSize());
    }

    @Test
    public void evictsOldestUnpinnedOverBudget() {
        ClientCacheBlobStore store = new ClientCacheBlobStore(100);
        byte[] a = bytes(60, (byte) 1);
        byte[] b = bytes(60, (byte) 2);
        byte[] c = bytes(10, (byte) 3);
        long ha = cn.nukkit.utils.XXHash64.hash(a);
        long hb = cn.nukkit.utils.XXHash64.hash(b);
        long hc = cn.nukkit.utils.XXHash64.hash(c);

        store.put(ha, a);
        store.put(hb, b); // 超预算，驱逐最旧未 pin 的 a
        assertFalse(store.contains(ha));
        assertTrue(store.contains(hb));
        assertEquals(60, store.byteSize());

        store.put(hc, c); // 未超预算，不驱逐
        assertTrue(store.contains(hb));
        assertTrue(store.contains(hc));
    }

    @Test
    public void pinProtectsFromEviction() {
        ClientCacheBlobStore store = new ClientCacheBlobStore(100);
        byte[] a = bytes(60, (byte) 1);
        byte[] b = bytes(60, (byte) 2);
        byte[] c = bytes(10, (byte) 3);
        long ha = cn.nukkit.utils.XXHash64.hash(a);
        long hb = cn.nukkit.utils.XXHash64.hash(b);
        long hc = cn.nukkit.utils.XXHash64.hash(c);

        store.put(ha, a);
        store.pin(ha);
        store.put(hb, b); // a 被 pin，允许临时超预算
        assertTrue(store.contains(ha));
        assertTrue(store.contains(hb));
        assertEquals(120, store.byteSize());

        store.put(hc, c); // 超预算 → 驱逐未 pin 的 b（a 跳过）
        assertTrue(store.contains(ha), "pinned blob must survive eviction");
        assertFalse(store.contains(hb));
        assertTrue(store.contains(hc));
    }

    @Test
    public void lruTouchProtectsRecentlyUsed() {
        ClientCacheBlobStore store = new ClientCacheBlobStore(100);
        byte[] a = bytes(60, (byte) 1);
        byte[] b = bytes(60, (byte) 2);
        byte[] c = bytes(10, (byte) 3);
        long ha = cn.nukkit.utils.XXHash64.hash(a);
        long hb = cn.nukkit.utils.XXHash64.hash(b);
        long hc = cn.nukkit.utils.XXHash64.hash(c);

        store.put(ha, a);
        store.put(hb, b); // a 被驱逐
        assertFalse(store.contains(ha));

        store.put(ha, a);
        store.get(ha); // touch → ha 变为最新
        store.put(hc, c); // 超预算驱逐最旧未 pin 的 hb
        assertTrue(store.contains(ha));
        assertFalse(store.contains(hb));
    }

    @Test
    public void pinRefcount() {
        ClientCacheBlobStore store = new ClientCacheBlobStore(1 << 20);
        byte[] a = bytes(10, (byte) 1);
        long ha = cn.nukkit.utils.XXHash64.hash(a);
        store.put(ha, a);
        store.pin(ha);
        store.pin(ha);
        assertTrue(store.isPinned(ha));
        store.unpin(ha);
        assertTrue(store.isPinned(ha), "one pin reference must remain");
        store.unpin(ha);
        assertFalse(store.isPinned(ha));
        // 多次 unpin 不产生负计数 / extra unpin must not underflow
        store.unpin(ha);
        assertFalse(store.isPinned(ha));
    }

    @Test
    public void knownContentDeduplicates() {
        ClientCacheBlobStore store = new ClientCacheBlobStore(1 << 20);
        byte[] a = "shared sub chunk".getBytes(StandardCharsets.US_ASCII);
        long h = cn.nukkit.utils.XXHash64.hash(a);
        store.put(h, a);
        store.put(h, a);
        assertEquals(1, store.size());
        assertEquals(a.length, store.byteSize());
    }

    @Test
    public void tryPinEnforcesPinnedByteHardCap() {
        ClientCacheBlobStore store = new ClientCacheBlobStore(100);
        byte[] a = bytes(60, (byte) 1);
        byte[] b = bytes(60, (byte) 2);
        long ha = cn.nukkit.utils.XXHash64.hash(a);
        long hb = cn.nukkit.utils.XXHash64.hash(b);
        store.put(ha, a);
        assertTrue(store.tryPin(ha));
        assertEquals(60, store.pinnedByteSize());
        // 第二块在首块已 pin 后再入库：不会被超预算驱逐（可 pin 的候选只剩 pin 保护的条目）
        // / The second blob enters after the first is pinned: over-budget eviction has
        // no unpinned candidate left to remove
        store.put(hb, b);
        assertFalse(store.tryPin(hb), "60 pinned + 60 > budget 100 must fail");
        assertEquals(60, store.pinnedByteSize());

        store.unpin(ha);
        assertEquals(0, store.pinnedByteSize());
        assertTrue(store.tryPin(hb), "unpin must restore pinned headroom");
        assertEquals(60, store.pinnedByteSize());
    }

    @Test
    public void refcountedRepinNeverFailsAndChargesOnce() {
        ClientCacheBlobStore store = new ClientCacheBlobStore(70);
        byte[] a = bytes(60, (byte) 1);
        long ha = cn.nukkit.utils.XXHash64.hash(a);
        store.put(ha, a);

        assertTrue(store.tryPin(ha));
        assertTrue(store.tryPin(ha), "second ref on an already-pinned hash adds no bytes");
        assertEquals(60, store.pinnedByteSize());
        store.unpin(ha);
        assertEquals(60, store.pinnedByteSize(), "one ref must remain");
        store.unpin(ha);
        assertEquals(0, store.pinnedByteSize());
    }

    @Test
    public void canPinBatchRejectsCollectiveOverage() {
        ClientCacheBlobStore store = new ClientCacheBlobStore(100);
        byte[] a = bytes(60, (byte) 1);
        byte[] b = bytes(60, (byte) 2);
        long ha = cn.nukkit.utils.XXHash64.hash(a);
        long hb = cn.nukkit.utils.XXHash64.hash(b);
        store.put(ha, a);
        assertTrue(store.tryPin(ha));
        store.put(hb, b);

        // 已 pin 的哈希零成本；未 pin 的按增量累计判顶 / already-pinned hashes are free;
        // unpinned ones are checked as an increment against the cap
        assertTrue(store.canPinBatch(new long[]{ha}));
        assertFalse(store.canPinBatch(new long[]{hb}), "60 pinned + 60 new > 100 must fail");
        assertFalse(store.canPinBatch(new long[]{ha, hb}), "the batch must fail through hb's increment");

        store.unpin(ha);
        assertTrue(store.canPinBatch(new long[]{hb}), "unpin must restore batch headroom");
    }

    @Test
    public void absentHashPinsFreeAndLatePutChargesPinnedBytes() {
        ClientCacheBlobStore store = new ClientCacheBlobStore(100);
        long h = 0xDEADBEEFL;
        assertTrue(store.tryPin(h), "absent hash pins for free (no content, no memory)");
        assertEquals(0, store.pinnedByteSize());

        byte[] payload = bytes(50, (byte) 7);
        store.put(h, payload);
        assertEquals(50, store.pinnedByteSize(), "late-arriving content for a pinned hash joins the pinned ledger");

        store.unpin(h);
        assertEquals(0, store.pinnedByteSize());
        assertTrue(store.contains(h), "unpin does not drop the content itself");
    }
}
