package cn.nukkit.network.session;

import cn.nukkit.network.CompressionProvider;
import cn.nukkit.network.protocol.BatchPacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.utils.BinaryStream;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Consumer;

/** Operation-owned scratch storage for the built-in synchronous compression providers. */
final class PacketBatcher {
    static final int MAX_BATCH_BYTES = 3 * 1024 * 1024;
    static final int MAX_RETAINED_BYTES = 1024 * 1024;
    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);
    private static final ClassValue<Boolean> DIRECT_BUFFER = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("getBuffer").getDeclaringClass() == BinaryStream.class
                        && type.getMethod("getBufferUnsafe").getDeclaringClass() == BinaryStream.class
                        && type.getMethod("getCount").getDeclaringClass() == BinaryStream.class;
            } catch (NoSuchMethodException impossible) {
                return false;
            }
        }
    };

    private PacketBatcher() { }

    static boolean supports(CompressionProvider provider) {
        // Plugins may provide codecs with different input ownership. Keep their original path.
        return provider == CompressionProvider.NONE || provider == CompressionProvider.ZLIB
                || provider == CompressionProvider.ZLIB_RAW || provider == CompressionProvider.SNAPPY
                || provider == CompressionProvider.NETEASE_UNKNOWN;
    }

    static byte[] compressBorrowed(CompressionProvider provider, BinaryStream batched, int level)
            throws Exception {
        // The provider can change while encode() or a preceding split callback runs.
        // Unknown codecs may retain their input, so only built-ins receive leased storage.
        BinaryStream input = supports(provider) ? batched : new BinaryStream(batched.getBuffer());
        return provider.compress(input, level);
    }

    /** The consumer must finish reading the stream before returning; it must not retain the stream. */
    static void send(Collection<DataPacket> packets, Consumer<BinaryStream> send) {
        Scratch scratch = SCRATCH.get();
        BinaryStream batched = scratch.idle;
        scratch.idle = null; // A reentrant send leases another stream, never the active one.
        if (batched == null) batched = new BinaryStream();
        try {
            for (DataPacket packet : packets) {
                if (packet instanceof BatchPacket) {
                    throw new IllegalArgumentException("Cannot batch BatchPacket");
                }
                packet.tryEncode();
                int length;
                byte[] source;
                boolean borrowed = DIRECT_BUFFER.get(packet.getClass());
                if (borrowed) {
                    length = packet.getCount();
                    source = packet.getBufferUnsafe();
                    // Preserve getBuffer's padding/error behavior for unusual custom packets.
                    if (source == null || length < 0 || length > source.length) {
                        source = packet.getBuffer();
                        length = source.length;
                        borrowed = false;
                    }
                } else {
                    // A plugin may transform the payload in getBuffer(). Preserve its virtual API.
                    source = packet.getBuffer();
                    length = source.length;
                }
                if (batched.getCount() + length > MAX_BATCH_BYTES) {
                    // The old path snapshots this packet BEFORE the preceding batch is sent.
                    // Keep that ownership boundary even when a send callback reenters or edits it.
                    if (borrowed) source = Arrays.copyOf(source, length);
                    send.accept(batched);
                    batched.reset();
                }
                append(batched, source, length);
            }
            send.accept(batched);
        } finally {
            batched.reset();
            if (batched.getBufferUnsafe().length <= MAX_RETAINED_BYTES && scratch.idle == null) {
                scratch.idle = batched;
            }
        }
    }

    private static void append(BinaryStream stream, byte[] source, int length) {
        int prefixBytes = 1;
        for (int remaining = length >>> 7; remaining != 0; remaining >>>= 7) prefixBytes++;
        int previousCount = stream.getCount();
        long required = (long) previousCount + prefixBytes + length;
        if (required > MAX_ARRAY_SIZE) throw new OutOfMemoryError("Packet batch is too large");
        byte[] buffer = stream.getBufferUnsafe();
        if (required > buffer.length) {
            int capacity = (int) Math.max(required, Math.min((long) buffer.length * 2, MAX_ARRAY_SIZE));
            stream.setBuffer(Arrays.copyOf(buffer, capacity));
            stream.setCount(previousCount);
        }
        stream.putUnsignedVarInt(length);
        System.arraycopy(source, 0, stream.getBufferUnsafe(), stream.getCount(), length);
        stream.setCount((int) required);
    }

    private static final class Scratch {
        private BinaryStream idle;
    }
}
