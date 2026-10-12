package cn.nukkit.network.session;

import cn.nukkit.network.CompressionProvider;
import cn.nukkit.network.protocol.DataPacket;

import java.util.ArrayDeque;
import java.util.List;

/**
 * Bounded retention of reliable batches already delivered by the transport. Transport ACKs
 * cannot recover an application-level discard. Decode and game work stay paced separately.
 * Methods also synchronize close from the main thread with the event-loop owner.
 */
final class InboundBatchBacklog {
    static final int MAX_BATCHES = 1200;
    static final long MAX_BYTES = 12L * 1024L * 1024L;

    private final ArrayDeque<EncodedBatch> encoded = new ArrayDeque<>();
    private long encodedBytes;
    private List<DataPacket> pendingDecoded;
    private boolean closed;

    synchronized boolean canOffer(int bytes) {
        return !closed && bytes >= 0 && encoded.size() < MAX_BATCHES
                && encodedBytes + bytes <= MAX_BYTES;
    }

    synchronized void offer(byte[] payload, boolean prefixed, CompressionProvider compression) {
        if (!canOffer(payload.length)) {
            throw new IllegalStateException("Inbound backlog admission was not reserved");
        }
        encoded.addLast(new EncodedBatch(payload, prefixed, compression));
        encodedBytes += payload.length;
    }

    synchronized EncodedBatch poll() {
        EncodedBatch batch = encoded.pollFirst();
        if (batch != null) encodedBytes -= batch.payload().length;
        return batch;
    }

    synchronized boolean isEmpty() {
        return encoded.isEmpty();
    }

    synchronized List<DataPacket> pendingDecoded() {
        return pendingDecoded;
    }

    synchronized void holdDecoded(List<DataPacket> packets) {
        if (!closed) pendingDecoded = packets;
    }

    synchronized void clearDecoded() {
        pendingDecoded = null;
    }

    synchronized int batchCount() {
        return encoded.size();
    }

    synchronized long byteCount() {
        return encodedBytes;
    }

    synchronized void close() {
        closed = true;
        encoded.clear();
        encodedBytes = 0L;
        pendingDecoded = null;
    }

    record EncodedBatch(byte[] payload, boolean prefixed, CompressionProvider compression) {
    }
}
