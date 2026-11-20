package cn.nukkit;

import cn.nukkit.level.PlayerSubChunkRequestState;
import cn.nukkit.network.process.processor.common.ClientCacheBlobStatusProcessor;
import cn.nukkit.network.protocol.ClientCacheMissResponsePacket;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ClientCacheMissResponseTest {

    /*
     * drain 路径的空缓存状态防御：积压仍在而 blobCacheState 为 null（mock/异常构造；生产中
     * clearBlobCacheState 会原子清空两者）时必须静默返回，不得走到 sendMissBatchWithinBudget
     * 的无守卫解引用——积压哈希在仓库有载荷时 sendable 恒 ≥1，state.resolve 必然触发。
     * / Null-blob-cache-state defense on the drain path: when a backlog survives while
     * blobCacheState is null (mocks/abnormal construction; production clears both
     * atomically), the drain must return silently instead of reaching the unguarded
     * dereferences in sendMissBatchWithinBudget — with a servable backlog entry,
     * sendable is always >= 1 and state.resolve fires unconditionally.
     */
    @Test
    public void drainWithNullBlobCacheStateSkipsSilently() {
        PlayerHandle handle = mock(PlayerHandle.class);
        PlayerSubChunkRequestState flow = new PlayerSubChunkRequestState();
        assertTrue(flow.tryDeferMissBatch(new long[]{0x1234567890abcdefL}));
        when(handle.getSubChunkRequestState()).thenReturn(flow);
        when(handle.getBlobCacheState()).thenReturn(null);

        assertDoesNotThrow(() -> ClientCacheBlobStatusProcessor.drainDeferredMissResponses(handle));
        assertEquals(1, flow.deferredMissHashCount(), "a null cache state skips the backlog without draining it");
    }

    /*
     * MissResponse 按字节预算拆包：一次 BlobStatus 回报可达数千 blob（客户端周期性批量
     * 回报，首见大视野全部 miss），此前单包聚合会是数 MB~数十 MB 的分配与压缩尖峰。
     * / MissResponse splitting by byte budget: one BlobStatus can carry thousands of blobs
     * (periodic batched reports; a fresh large view distance is all misses) — the previous
     * single-packet aggregation was a multi-MB allocation and compression spike.
     */
    private static final int BUDGET = 2 * 1024 * 1024;

    @Test
    public void splitsOverBudgetPreservingOrder() {
        LongArrayList hashes = new LongArrayList();
        ObjectArrayList<byte[]> payloads = new ObjectArrayList<>();
        for (long h = 0; h < 100; h++) {
            hashes.add(h);
            payloads.add(new byte[100_000]);
        }

        List<ClientCacheMissResponsePacket> packets = ClientCacheBlobStatusProcessor.buildMissResponsePackets(hashes, payloads);

        // 条目计 13 + 100000 字节：每包 20 条（2,000,260 ≤ 2MB；21 条即超）
        // / Entries count 13 + 100000 bytes: 20 per packet (2,000,260 <= 2MB; 21 over)
        assertEquals(5, packets.size());
        long next = 0;
        for (ClientCacheMissResponsePacket pk : packets) {
            assertEquals(20, pk.hashes.length);
            assertEquals(20, pk.payloads.length);
            for (int i = 0; i < pk.hashes.length; i++) {
                assertEquals(next++, pk.hashes[i], "hash order must be preserved across packets");
                assertEquals(100_000, pk.payloads[i].length);
            }
            assertDoesNotThrow(pk::tryEncode);
        }
        assertEquals(100, next);
    }

    @Test
    public void everyPacketStaysWithinBudget() {
        LongArrayList hashes = new LongArrayList();
        ObjectArrayList<byte[]> payloads = new ObjectArrayList<>();
        for (long h = 0; h < 50; h++) {
            hashes.add(h);
            payloads.add(new byte[(int) (37_000 + h * 977)]);
        }

        for (ClientCacheMissResponsePacket pk : ClientCacheBlobStatusProcessor.buildMissResponsePackets(hashes, payloads)) {
            int bytes = 0;
            for (byte[] payload : pk.payloads) {
                bytes += 13 + payload.length;
            }
            assertTrue(bytes <= BUDGET, "packed bytes " + bytes + " must stay within the budget");
        }
    }

    @Test
    public void singleOversizedEntryGetsItsOwnPacket() {
        LongArrayList hashes = new LongArrayList();
        ObjectArrayList<byte[]> payloads = new ObjectArrayList<>();
        hashes.add(1L);
        payloads.add(new byte[3 * 1024 * 1024]);

        List<ClientCacheMissResponsePacket> packets = ClientCacheBlobStatusProcessor.buildMissResponsePackets(hashes, payloads);

        assertEquals(1, packets.size());
        assertEquals(1, packets.get(0).hashes.length);
        assertEquals(1, packets.get(0).payloads.length);
    }

    @Test
    public void smallBatchKeepsSinglePacket() {
        LongArrayList hashes = new LongArrayList();
        ObjectArrayList<byte[]> payloads = new ObjectArrayList<>();
        for (long h = 0; h < 3; h++) {
            hashes.add(h);
            payloads.add(new byte[64]);
        }

        List<ClientCacheMissResponsePacket> packets = ClientCacheBlobStatusProcessor.buildMissResponsePackets(hashes, payloads);

        assertEquals(1, packets.size());
        assertEquals(3, packets.get(0).hashes.length);
    }

    @Test
    public void emptyInputYieldsNoPackets() {
        assertTrue(ClientCacheBlobStatusProcessor.buildMissResponsePackets(new LongArrayList(), new ObjectArrayList<>()).isEmpty());
    }

    @Test
    public void prefixAlwaysIncludesTheFirstEntry() {
        ObjectArrayList<byte[]> payloads = new ObjectArrayList<>();
        payloads.add(new byte[50]);
        payloads.add(new byte[50]);
        // 预算 0 也取首条：保证进展，与子区块应答的 stopAt >= 1 同型 / budget 0 still
        // serves one entry for progress, mirroring the sub-chunk stopAt >= 1
        assertEquals(1, ClientCacheBlobStatusProcessor.missPrefixWithinByteBudget(payloads, 0));
        assertEquals(0, ClientCacheBlobStatusProcessor.missPrefixWithinByteBudget(new ObjectArrayList<>(), 0));
    }

    @Test
    public void prefixAccumulatesWhileEntriesFit() {
        ObjectArrayList<byte[]> payloads = new ObjectArrayList<>();
        for (int i = 0; i < 5; i++) {
            payloads.add(new byte[100]);
        }
        // 首条必取，其后按预算累加：每条 113 字节，339 = 3 × 113 → 前 3 条 / the first
        // entry is always taken, the rest accumulate while they fit: 113 each,
        // 339 = 3 × 113 → the first 3
        assertEquals(3, ClientCacheBlobStatusProcessor.missPrefixWithinByteBudget(payloads, 3 * 113));
        assertEquals(5, ClientCacheBlobStatusProcessor.missPrefixWithinByteBudget(payloads, Integer.MAX_VALUE));
    }
}
