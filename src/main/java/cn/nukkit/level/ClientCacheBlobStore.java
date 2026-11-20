package cn.nukkit.level;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 客户端缓存 blob 全服共享仓库：内容寻址（xxhash64）→ 序列化字节，带字节预算 LRU 与事务 pin，
 * 跨世界内容去重。仅主线程访问。
 * <p>
 * Server-wide shared blob store for the client chunk cache: content-addressed (xxhash64)
 * serialized bytes with a byte-budget LRU and transaction pins, deduplicated across levels.
 * Main-thread only (all access is marshalled through the chunk send callback queue or packet
 * processors).
 * <p>
 * pin 字节有硬顶（= 总预算 maxBytes）：不回 BlobStatus 的客户端钉住 pending 上限个 blob 也
 * 只能占满预算，不能把仓库撑超；超顶的 pin 失败，调用方走既有的全量/raw 降级路径。
 * <p>
 * Pinned bytes are hard-capped at the total budget (maxBytes): a client that never answers
 * BlobStatus can at most pin its pending cap's worth up to the budget, never beyond; a pin
 * over the cap fails and the caller takes the existing full-send/raw degradation path.
 */
public class ClientCacheBlobStore {

    private final LinkedHashMap<Long, byte[]> lru;
    private final Long2ObjectOpenHashMap<byte[]> blobs;
    // pin 引用计数：所有未关闭事务引用的哈希不可被 LRU 驱逐 / Pin refcounts: hashes referenced
    // by open transactions are never evicted by the LRU
    private final Long2IntOpenHashMap pins = new Long2IntOpenHashMap();

    private final long maxBytes;
    private long bytes;
    // 已 pin 条目占用的字节（同一哈希多份引用只计一份；未入库的 pin 不占字节）
    // / Bytes occupied by pinned entries (one charge per distinct hash; pins on absent
    // hashes charge nothing)
    private long pinnedBytes;

    public ClientCacheBlobStore(long maxBytes) {
        this.maxBytes = Math.max(1, maxBytes);
        this.lru = new LinkedHashMap<>(16, 0.75f, true);
        this.blobs = new Long2ObjectOpenHashMap<>();
        this.pins.defaultReturnValue(0);
    }

    /**
     * 写入 blob（已存在则不动）。新写入超预算时先驱逐其他未 pin 的最旧条目；
     * 刚写入的条目自身永不驱逐（允许临时超预算）。
     * <p>
     * Puts a blob (no-op if present). Evicts other oldest unpinned entries when over
     * budget; the entry just added is never evicted itself (temporary overage is permitted).
     */
    public void put(long hash, byte[] payload) {
        if (this.blobs.containsKey(hash)) {
            return;
        }
        this.blobs.put(hash, payload);
        this.lru.put(hash, payload);
        this.bytes += payload.length;
        if (this.pins.get(hash) > 0) {
            // 迟到的内容落库时哈希已被 pin：此刻起计入 pinned 字节 / content arriving
            // for an already-pinned hash: its bytes join the pinned ledger from now on
            this.pinnedBytes += payload.length;
        }
        this.evictOverBudget(hash);
    }

    public byte[] get(long hash) {
        byte[] payload = this.blobs.get(hash);
        if (payload != null) {
            this.lru.get(hash); // touch for LRU order
        }
        return payload;
    }

    public boolean contains(long hash) {
        return this.blobs.containsKey(hash);
    }

    /**
     * 尝试 pin 单个哈希：入库且未 pin 的哈希在超出 pinned 字节硬顶时失败（未入库的哈希
     * 零成本可 pin，语义为「无内容可占内存」）。
     * <p>
     * Tries to pin a single hash: a present, so-far-unpinned hash fails over the pinned
     * byte hard cap (absent hashes pin for free — no content, no memory).
     */
    public boolean tryPin(long hash) {
        byte[] payload = this.blobs.get(hash);
        if (payload != null && this.pins.get(hash) == 0
                && this.pinnedBytes + payload.length > this.maxBytes) {
            return false;
        }
        this.pin(hash);
        return true;
    }

    /**
     * 预检一批哈希全部 pin 的字节需求（已 pin / 未入库的哈希零成本）。事务提交前调用，
     * 通过后该批的 pin 不会再失败。
     * <p>
     * Pre-checks the byte demand of pinning a whole batch (already-pinned / absent hashes
     * cost nothing). Call before a transaction commit; once passed, the batch's pins
     * cannot fail.
     */
    public boolean canPinBatch(long[] hashes) {
        long total = this.pinnedBytes;
        for (long hash : hashes) {
            if (this.pins.get(hash) > 0) {
                continue;
            }
            byte[] payload = this.blobs.get(hash);
            if (payload != null && (total += payload.length) > this.maxBytes) {
                return false;
            }
        }
        return true;
    }

    /**
     * 无检查 pin（事务提交路径专用，前置 {@link #canPinBatch} 后调用保证不超顶）。
     * <p>
     * Unchecked pin (transaction commit path only; call after {@link #canPinBatch}).
     */
    void pin(long hash) {
        byte[] payload = this.blobs.get(hash);
        if (payload != null && this.pins.get(hash) == 0) {
            this.pinnedBytes += payload.length;
        }
        this.pins.put(hash, this.pins.get(hash) + 1);
    }

    public void unpin(long hash) {
        int count = this.pins.get(hash);
        if (count <= 1) {
            this.pins.remove(hash);
        } else {
            this.pins.put(hash, count - 1);
        }
        if (count == 1) {
            byte[] payload = this.blobs.get(hash);
            if (payload != null) {
                this.pinnedBytes -= payload.length;
            }
        }
    }

    boolean isPinned(long hash) {
        return this.pins.get(hash) > 0;
    }

    public int size() {
        return this.blobs.size();
    }

    public long byteSize() {
        return this.bytes;
    }

    public long pinnedByteSize() {
        return this.pinnedBytes;
    }

    /**
     * 超预算时从最旧开始驱逐未 pin 的 blob；pin 住的条目跳过（允许临时超预算）。
     * <p>
     * Evicts unpinned entries oldest-first while over budget; pinned entries are
     * skipped (temporary overage is permitted).
     */
    private void evictOverBudget(long justAdded) {
        if (this.bytes <= this.maxBytes || this.lru.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<Long, byte[]>> it = this.lru.entrySet().iterator();
        while (this.bytes > this.maxBytes && it.hasNext()) {
            Map.Entry<Long, byte[]> entry = it.next();
            long key = entry.getKey();
            if (key == justAdded || this.pins.get(key) > 0) {
                continue;
            }
            it.remove();
            this.blobs.remove(key);
            this.bytes -= entry.getValue().length;
        }
    }
}
