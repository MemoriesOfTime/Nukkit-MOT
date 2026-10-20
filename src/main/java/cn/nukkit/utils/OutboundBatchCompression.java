package cn.nukkit.utils;

import cn.nukkit.Server;
import cn.nukkit.network.CompressionProvider;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/** Reuses only identical encoded batches, before transport framing and encryption. */
public final class OutboundBatchCompression {
    private static final int MAX_BATCH_BYTES = 64 * 1024;
    private static final int MAX_FLIGHTS_PER_SLOT = 2;
    private static final long MAX_AGE_NANOS = 1_000_000_000L;
    private static final Slot[] SLOTS = new Slot[16];

    static {
        for (int i = 0; i < SLOTS.length; i++) SLOTS[i] = new Slot();
    }

    private OutboundBatchCompression() { }

    /** Returned bytes are shared read-only; each session still sends its own frame. */
    public static byte[] compress(CompressionProvider compression, BinaryStream batch,
                                  int configuredLevel, int protocol) throws Exception {
        if (compression != CompressionProvider.ZLIB_RAW && compression != CompressionProvider.SNAPPY) {
            return compression.compress(batch, configuredLevel);
        }
        // Only plain streams have known getter semantics and a caller-owned buffer.
        // Borrow its logical range for lookup; never retain or compress it directly.
        byte[] raw = batch.getClass() == BinaryStream.class ? batch.getBufferUnsafe() : null;
        int count = raw == null ? -1 : batch.getCount();
        boolean borrowed = raw != null && count >= 0 && count <= raw.length;
        // Virtual getters may change the provider: call them outside its read fence.
        // Preserve zero padding and exceptions for unusual BinaryStream states.
        byte[] bytes = borrowed ? raw : batch.getBuffer();
        int size = borrowed ? count : bytes.length;
        if (compression == CompressionProvider.SNAPPY) {
            return cached(compression, null, bytes, size, borrowed, configuredLevel, protocol, 0);
        }
        // Provider 1 ignores level; preserve its configured argument and original bytes.
        Attempt attempt = Zlib.withStableCompression(backend -> {
            try {
                int level = configuredLevel;
                return new Attempt(cached(compression, backend, bytes, size, borrowed, level,
                        protocol, Server.getInstance().networkCompressionThreshold), null);
            } catch (Exception failure) {
                return new Attempt(null, failure);
            }
        });
        if (attempt.failure != null) throw attempt.failure;
        return attempt.bytes;
    }

    private static byte[] cached(CompressionProvider compression, ZlibProvider backend, byte[] bytes,
                                 int size, boolean borrowed, int level, int protocol, int threshold) throws Exception {
        if (size > MAX_BATCH_BYTES) {
            return encode(compression, backend, borrowed ? Arrays.copyOf(bytes, size) : bytes, level);
        }
        // Sampling chooses a slot only; full equality below decides reuse. Avoid a
        // complete hash pass on unique large batches, where compression must run anyway.
        int hash = slotHash(bytes, size);
        hash = 31 * hash + protocol;
        hash = 31 * hash + level;
        hash = 31 * hash + threshold;
        hash = 31 * hash + System.identityHashCode(compression);
        hash = 31 * hash + System.identityHashCode(backend);
        hash ^= hash >>> 16;
        Slot slot = SLOTS[hash & (SLOTS.length - 1)];
        for (;;) {
            Flight flight = null;
            boolean leader = true;
            byte[] input = null;
            synchronized (slot) {
                Entry e = slot.entry;
                if (e != null && slot.entryHash == hash
                        && System.nanoTime() - e.created < MAX_AGE_NANOS
                        && matches(e, compression, backend, bytes, size, level, protocol, threshold)) {
                    return e.output;
                }
                int active = 0;
                for (Flight f = slot.flights; f != null; f = f.next) {
                    active++;
                    if (matches(f.key, compression, backend, bytes, size, level, protocol, threshold)) {
                        flight = f;
                        leader = false;
                        break;
                    }
                }
                if (leader) {
                    // Own the snapshot before callbacks; lookup never retains the caller's array.
                    input = Arrays.copyOf(bytes, size);
                    if (active < MAX_FLIGHTS_PER_SLOT) {
                        flight = new Flight(new Entry(compression, backend, protocol, level, threshold,
                                input, null, 0), slot.flights);
                        slot.flights = flight;
                    }
                }
            }
            if (!leader) {
                // join preserves interrupt status. Use this flight's result even if a
                // colliding completion replaces the cache entry before the waiter runs.
                byte[] output = flight.result.join();
                if (output != null) return output;
                // Only the failed leader throws; existing waiters retry the lookup.
                continue;
            }
            try {
                // Distinct contents may compress concurrently, including slot collisions.
                byte[] output = encode(compression, backend, input, level);
                if (flight != null) {
                    synchronized (slot) {
                        if (output.length <= MAX_BATCH_BYTES) {
                            slot.entry = new Entry(compression, backend, protocol, level, threshold,
                                    input, output, System.nanoTime());
                            // Publish the hint with its entry under the same monitor.
                            slot.entryHash = hash;
                        }
                        unlink(slot, flight);
                    }
                    flight.result.complete(output);
                }
                // At capacity, bypass retention without blocking another key's compression.
                return output;
            } catch (Throwable failure) {
                if (flight != null) {
                    synchronized (slot) { unlink(slot, flight); }
                    flight.result.complete(null);
                }
                throw failure;
            }
        }
    }

    private static boolean matches(Entry e, CompressionProvider compression, ZlibProvider backend,
                                   byte[] bytes, int size, int level, int protocol, int threshold) {
        return e.compression == compression && e.backend == backend
                && e.protocol == protocol && e.level == level && e.threshold == threshold
                && e.input.length == size && Arrays.equals(e.input, 0, size, bytes, 0, size);
    }

    // Called only under the slot monitor; at most two linked flights per slot.
    private static void unlink(Slot slot, Flight flight) {
        Flight previous = null;
        for (Flight current = slot.flights; current != null; current = current.next) {
            if (current == flight) {
                if (previous == null) slot.flights = current.next;
                else previous.next = current.next;
                current.next = null;
                return;
            }
            previous = current;
        }
    }

    private static int slotHash(byte[] bytes, int size) {
        if (size <= 64) {
            int hash = 1;
            for (int i = 0; i < size; i++) hash = 31 * hash + bytes[i];
            return hash;
        }
        int hash = size;
        for (int i = 0; i < 16; i++) {
            hash = 31 * hash + bytes[i];
            hash = 31 * hash + bytes[(size >>> 1) - 8 + i];
            hash = 31 * hash + bytes[size - 16 + i];
        }
        return hash;
    }

    private static byte[] encode(CompressionProvider compression, ZlibProvider backend,
                                 byte[] bytes, int level) throws Exception {
        return compression == CompressionProvider.SNAPPY
                ? SnappyCompression.compress(bytes) : backend.deflateRaw(bytes, level);
    }

    private static final class Slot {
        private Entry entry;
        private int entryHash;
        private Flight flights;
    }
    private static final class Flight {
        private final Entry key;
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private Flight next;
        private Flight(Entry key, Flight next) { this.key = key; this.next = next; }
    }
    private record Attempt(byte[] bytes, Exception failure) { }
    private record Entry(CompressionProvider compression, ZlibProvider backend, int protocol,
                         int level, int threshold, byte[] input, byte[] output, long created) { }
}
