package cn.nukkit.network.process.processor.common;

import cn.nukkit.GameVersion;
import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.level.ClientCacheBlobStore;
import cn.nukkit.level.PlayerBlobCacheState;
import cn.nukkit.level.PlayerSubChunkRequestState;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.ClientCacheBlobStatusPacket;
import cn.nukkit.network.protocol.ClientCacheMissResponsePacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端缓存 hit/miss 回报：hit 解除事务；miss 从 blob 仓库补发原文（按字节预算拆包，
 * 且与子区块应答共用每 tick 字节账本，超预算部分积压逐 tick 排空，杜绝压缩尖峰）。
 * <p>
 * Client cache hit/miss status: hits resolve transactions; misses are re-served
 * from the blob store via ClientCacheMissResponse (split by a byte budget and
 * sharing the per-tick byte ledger with sub-chunk answers; the over-budget part
 * backlogs and drains over ticks, ruling out compression spikes).
 *
 * @author LT_Name
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClientCacheBlobStatusProcessor extends DataPacketProcessor<ClientCacheBlobStatusPacket> {

    public static final ClientCacheBlobStatusProcessor INSTANCE = new ClientCacheBlobStatusProcessor();

    // miss 补发单包字节预算，超出拆分为多个包（条目自描述，分批合法），与子区块响应同量级
    // / MissResponse byte budget per packet; larger responses split into multiple packets
    // (entries are self-describing, partial batches are legal), matching the sub-chunk cap
    private static final int MAX_MISS_RESPONSE_PACKET_BYTES = 2 * 1024 * 1024;

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull ClientCacheBlobStatusPacket pk) {
        Player player = playerHandle.player;
        PlayerBlobCacheState state = playerHandle.getBlobCacheState();
        if (state == null) {
            return;
        }
        for (long hash : pk.hitHashes) {
            state.resolve(hash);
        }
        if (pk.missHashes.length == 0) {
            return;
        }
        ClientCacheBlobStore store = player.getServer().getClientCacheBlobStore();
        LongArrayList missHashList = new LongArrayList(pk.missHashes.length);
        ObjectArrayList<byte[]> missPayloadList = new ObjectArrayList<>(pk.missHashes.length);
        for (long hash : pk.missHashes) {
            byte[] payload = store.get(hash);
            if (payload != null) {
                missHashList.add(hash);
                missPayloadList.add(payload);
            } else {
                // 已驱逐的哈希多为重复内容，客户端通常已持有 / evicted hashes are usually
                // duplicates the client already holds
                state.resolve(hash);
            }
        }
        if (!missHashList.isEmpty()) {
            sendMissBatchWithinBudget(playerHandle, state, missHashList, missPayloadList);
        }
    }

    /**
     * 每 tick 排空积压的 miss 补发（Player.checkNetwork 调用）：与子区块应答共用本 tick
     * 字节账本，至少补发一条保证进展，预算耗尽即止、剩余推回队头。应先于子区块排空——
     * miss 兑现的是已发出的哈希引用，子区块队列可多等一 tick。
     * <p>
     * Drains the miss-response backlog each tick (called from Player.checkNetwork):
     * shares this tick's byte ledger with sub-chunk answers, always serves at least
     * one entry for progress, and stops on budget exhaustion with the residual
     * pushed back. Runs before the sub-chunk drain — misses fulfill already-sent
     * hash references, while the sub-chunk queue can wait another tick.
     */
    public static void drainDeferredMissResponses(PlayerHandle playerHandle) {
        PlayerSubChunkRequestState flow = playerHandle.getSubChunkRequestState();
        if (flow == null || flow.deferredMissHashCount() == 0) {
            return;
        }
        Player player = playerHandle.player;
        PlayerBlobCacheState state = playerHandle.getBlobCacheState();
        if (state == null) {
            // 与 handle() 同型：无缓存状态（mock/异常构造——生产中 clearBlobCacheState 会原子
            // 清空积压）时静默跳过，避免下游 state.resolve 无守卫解引用
            // / Mirrors handle(): with no cache state (mocks/abnormal construction —
            // production clears the backlog atomically) skip silently, avoiding the
            // unguarded state.resolve dereferences downstream
            return;
        }
        ClientCacheBlobStore store = player.getServer().getClientCacheBlobStore();
        int tick = player.getServer().getTick();
        long[] batch;
        while ((batch = flow.pollMissBatch()) != null) {
            LongArrayList hashes = new LongArrayList(batch.length);
            ObjectArrayList<byte[]> payloads = new ObjectArrayList<>(batch.length);
            for (long hash : batch) {
                byte[] payload = store.get(hash);
                if (payload != null) {
                    hashes.add(hash);
                    payloads.add(payload);
                } else {
                    state.resolve(hash);
                }
            }
            if (hashes.isEmpty()) {
                continue;
            }
            boolean fullySent = sendMissBatchWithinBudget(playerHandle, state, hashes, payloads);
            if (!fullySent) {
                break;
            }
        }
    }

    /**
     * 在本 tick 剩余字节预算内补发 miss（至少一条，防饿死），超出部分积压到
     * {@link PlayerSubChunkRequestState} 逐 tick 排空；只有已补发或被丢弃的哈希才
     * resolve 释放 pin，积压中的哈希保持 pin，避免补发前被 LRU 驱逐。
     * <p>
     * Serves misses within this tick's remaining byte budget (at least one entry,
     * against starvation); the rest backlogs in {@link PlayerSubChunkRequestState}
     * for per-tick draining. Only served or dropped hashes resolve (releasing their
     * pin); backlogged hashes stay pinned so the LRU cannot evict them before
     * delivery.
     *
     * @return 是否整批已补发（无积压）/ whether the whole batch was served (nothing backlogged)
     */
    private static boolean sendMissBatchWithinBudget(PlayerHandle playerHandle, PlayerBlobCacheState state,
                                                     LongArrayList hashes, ObjectArrayList<byte[]> payloads) {
        Player player = playerHandle.player;
        PlayerSubChunkRequestState flow = playerHandle.getSubChunkRequestState();
        int tick = player.getServer().getTick();
        int allowed = flow == null ? Integer.MAX_VALUE : flow.remainingResponseBytes(tick);
        int sendable = missPrefixWithinByteBudget(payloads, allowed);
        if (sendable > 0) {
            int bytes = 0;
            LongArrayList sendHashes = new LongArrayList(sendable);
            ObjectArrayList<byte[]> sendPayloads = new ObjectArrayList<>(sendable);
            for (int i = 0; i < sendable; i++) {
                sendHashes.add(hashes.getLong(i));
                sendPayloads.add(payloads.get(i));
                bytes += missEntryBytes(payloads.get(i).length);
            }
            for (ClientCacheMissResponsePacket missResponse : buildMissResponsePackets(sendHashes, sendPayloads)) {
                player.dataPacket(missResponse);
            }
            if (flow != null) {
                flow.accountResponseBytes(tick, bytes);
            }
            for (int i = 0; i < sendable; i++) {
                state.resolve(hashes.getLong(i));
            }
        }
        if (sendable < hashes.size()) {
            long[] leftover = new long[hashes.size() - sendable];
            for (int i = sendable; i < hashes.size(); i++) {
                leftover[i - sendable] = hashes.getLong(i);
            }
            if (flow == null || !flow.tryDeferMissBatch(leftover)) {
                // 无流量状态（mock 等）或积压超限：直接释放，客户端经重新请求重试
                // / No flow state (mocks) or backlog over cap: release right away;
                // the client retries via re-requests
                for (long hash : leftover) {
                    state.resolve(hash);
                }
                return true;
            }
            return false;
        }
        return true;
    }

    /**
     * miss 条目近似线上字节数：LLong 8 + 长度前缀 varint 上界 5 + 载荷。
     * <p>
     * Approximate wire bytes of a miss entry: LLong 8 + up to 5 varint length prefix + payload.
     */
    public static int missEntryBytes(int payloadLength) {
        return 13 + payloadLength;
    }

    /**
     * 本 tick 预算内可补发的前缀条数：首条必取（保证进展，与子区块应答的 stopAt≥1 同型），
     * 其后按预算累加；预算 0 或整批放不下时为 1。
     * <p>
     * Prefix entry count servable within this tick's budget: the first entry is
     * always taken (progress guarantee, mirroring the sub-chunk stopAt >= 1); the
     * rest accumulate while they fit. 1 when the budget is 0 or nothing fits.
     */
    public static int missPrefixWithinByteBudget(ObjectArrayList<byte[]> payloads, int allowedBytes) {
        int bytes = 0;
        int i = 0;
        for (; i < payloads.size(); i++) {
            int entryBytes = missEntryBytes(payloads.get(i).length);
            if (i > 0 && bytes + entryBytes > allowedBytes) {
                break;
            }
            bytes += entryBytes;
        }
        return i;
    }

    /**
     * miss 补发按字节预算拆包：一次 BlobStatus 回报可达数千 blob（客户端周期性批量回报，
     * 首见大视野全部 miss），单包聚合会是数 MB~数十 MB 的分配与压缩尖峰。无法整装的
     * 超大单条目（预算内放不下）独占一包。
     * <p>
     * Splits miss responses by a byte budget: one BlobStatus can carry thousands of
     * blobs (the client reports in periodic batches; a fresh large view distance is all
     * misses), and aggregating them into a single packet would be a multi-MB allocation
     * and compression spike. A single oversized entry that cannot fit gets a packet of
     * its own.
     */
    public static List<ClientCacheMissResponsePacket> buildMissResponsePackets(LongArrayList hashes, ObjectArrayList<byte[]> payloads) {
        List<ClientCacheMissResponsePacket> packets = new ArrayList<>();
        ClientCacheMissResponsePacket current = new ClientCacheMissResponsePacket();
        LongArrayList currentHashes = new LongArrayList();
        ObjectArrayList<byte[]> currentPayloads = new ObjectArrayList<>();
        int bytes = 0;
        for (int i = 0; i < hashes.size(); i++) {
            int entryBytes = missEntryBytes(payloads.get(i).length);
            if (bytes + entryBytes > MAX_MISS_RESPONSE_PACKET_BYTES && !currentHashes.isEmpty()) {
                current.hashes = currentHashes.toLongArray();
                current.payloads = currentPayloads.toArray(new byte[0][]);
                packets.add(current);
                current = new ClientCacheMissResponsePacket();
                currentHashes = new LongArrayList();
                currentPayloads = new ObjectArrayList<>();
                bytes = 0;
            }
            currentHashes.add(hashes.getLong(i));
            currentPayloads.add(payloads.get(i));
            bytes += entryBytes;
        }
        if (!currentHashes.isEmpty()) {
            current.hashes = currentHashes.toLongArray();
            current.payloads = currentPayloads.toArray(new byte[0][]);
            packets.add(current);
        }
        return packets;
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.toNewProtocolID(ProtocolInfo.CLIENT_CACHE_BLOB_STATUS_PACKET);
    }

    @Override
    public Class<? extends DataPacket> getPacketClass() {
        return ClientCacheBlobStatusPacket.class;
    }

    @Override
    public boolean isSupported(GameVersion gameVersion) {
        return gameVersion.getProtocol() >= ProtocolInfo.v1_12_0;
    }
}
