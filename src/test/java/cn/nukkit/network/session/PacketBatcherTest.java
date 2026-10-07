package cn.nukkit.network.session;

import cn.nukkit.MockServer;
import cn.nukkit.network.CompressionProvider;
import cn.nukkit.network.protocol.BatchPacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.utils.BinaryStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class PacketBatcherTest {
    @BeforeAll
    static void initialize() { MockServer.init(); }

    @Test
    void framingAndSplitBoundariesMatchTheFormerEncoderByteForByte() {
        for (int[] sizes : new int[][]{{}, {0}, {1, 127, 128, 16383, 16384},
                {2097151, 1048571, 0, 1}, {PacketBatcher.MAX_BATCH_BYTES - 4, 1, 2},
                {PacketBatcher.MAX_BATCH_BYTES, 1}, {PacketBatcher.MAX_BATCH_BYTES + 1, 0}}) {
            List<DataPacket> packets = new ArrayList<>();
            for (int size : sizes) packets.add(new RawPacket(size, 19));
            List<byte[]> expected = collect(packets, false);
            List<byte[]> actual = collect(packets, true);
            assertEquals(expected.size(), actual.size(), Arrays.toString(sizes));
            for (int i = 0; i < expected.size(); i++) {
                assertArrayEquals(expected.get(i), actual.get(i), Arrays.toString(sizes) + " batch " + i);
            }
        }
    }

    @Test
    void randomBatchesAndSparePacketCapacityDoNotLeakPadding() {
        Random random = new Random(26_09_2026);
        for (int run = 0; run < 30; run++) {
            List<DataPacket> packets = new ArrayList<>();
            for (int p = 0; p < 20; p++) packets.add(new RawPacket(random.nextInt(32000), 67));
            assertArrayEquals(collect(packets, false).get(0), collect(packets, true).get(0));
        }
    }

    @Test
    void customGetBufferOverridesKeepTheirPayloadAndInvocationCount() {
        int[] calls = {0};
        RawPacket packet = new RawPacket(8192, 512) {
            @Override public byte[] getBuffer() {
                calls[0]++;
                return new byte[]{4, 5, 6};
            }
        };
        assertArrayEquals(new byte[]{3, 4, 5, 6}, collect(List.of(packet), true).get(0));
        assertEquals(1, calls[0]);
        calls[0] = 0;
        List<DataPacket> split = List.of(new RawPacket(PacketBatcher.MAX_BATCH_BYTES, 0), packet);
        List<byte[]> result = collect(split, true);
        assertArrayEquals(new byte[]{3, 4, 5, 6}, result.get(result.size() - 1));
        assertEquals(1, calls[0]);
    }

    @Test
    void splitSnapshotsThePendingPacketBeforeInvokingThePreviousSend() {
        RawPacket first = new RawPacket(PacketBatcher.MAX_BATCH_BYTES - 1, 0);
        RawPacket second = new RawPacket(50, 12);
        List<DataPacket> packets = List.of(first, second);
        List<byte[]> expected = collect(packets, false);
        List<byte[]> actual = new ArrayList<>();
        PacketBatcher.send(packets, stream -> {
            actual.add(stream.getBuffer());
            Arrays.fill(second.getBufferUnsafe(), (byte) 41);
        });
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) assertArrayEquals(expected.get(i), actual.get(i));
    }

    @Test
    void allBuiltInCodecsKeepOwnedResultsAfterScratchReuse() throws Exception {
        List<DataPacket> packets = List.of(new RawPacket(32768, 55), new RawPacket(101, 17));
        byte[] expected = collect(packets, false).get(0);
        for (CompressionProvider provider : new CompressionProvider[]{CompressionProvider.NONE,
                CompressionProvider.ZLIB, CompressionProvider.ZLIB_RAW, CompressionProvider.SNAPPY,
                CompressionProvider.NETEASE_UNKNOWN}) {
            AtomicReference<byte[]> compressed = new AtomicReference<>();
            PacketBatcher.send(packets, stream -> {
                try { compressed.set(provider.compress(stream, 1)); }
                catch (Exception failure) { throw new AssertionError(failure); }
            });
            byte[] beforeReuse = compressed.get().clone();
            PacketBatcher.send(List.of(new RawPacket(65536, 99)), ignored -> { });
            assertArrayEquals(beforeReuse, compressed.get());
            CompressionProvider decoder = provider == CompressionProvider.NETEASE_UNKNOWN
                    ? CompressionProvider.ZLIB_RAW : provider;
            assertArrayEquals(expected, decoder.decompress(compressed.get()));
        }
    }

    @Test
    void nestedSendNeverOverwritesTheActiveOuterBuffer() {
        PacketBatcher.send(List.of(new RawPacket(512, 0)), outer -> {
            byte[] saved = outer.getBuffer();
            PacketBatcher.send(List.of(new RawPacket(8192, 0)), inner -> assertNotSame(outer, inner));
            assertArrayEquals(saved, outer.getBuffer());
        });
    }

    @Test
    void ordinaryBuffersAreReusedButOversizedBuffersAreNotRetained() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> {
                AtomicReference<BinaryStream> first = new AtomicReference<>();
                PacketBatcher.send(List.of(new RawPacket(4096, 0)), first::set);
                PacketBatcher.send(List.of(new RawPacket(100, 0)), stream -> assertSame(first.get(), stream));
                PacketBatcher.send(List.of(new RawPacket(PacketBatcher.MAX_RETAINED_BYTES + 1, 0)), first::set);
                PacketBatcher.send(List.of(new RawPacket(1, 0)), stream -> {
                    assertNotSame(first.get(), stream);
                    assertTrue(stream.getBufferUnsafe().length <= PacketBatcher.MAX_RETAINED_BYTES);
                });
            }).get();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void throwingSendAndEncodingFailureReleaseCleanScratch() {
        assertThrows(IllegalStateException.class, () -> PacketBatcher.send(List.of(new RawPacket(500, 0)),
                ignored -> { throw new IllegalStateException("send failed"); }));
        assertThrows(IllegalArgumentException.class, () -> PacketBatcher.send(
                List.of(new RawPacket(90, 0), new BatchPacket()), ignored -> { }));
        List<DataPacket> next = List.of(new RawPacket(71, 0));
        assertArrayEquals(collect(next, false).get(0), collect(next, true).get(0));
    }

    @Test
    void concurrentThreadsUseDifferentScratchStreams() throws Exception {
        AtomicReference<BinaryStream> main = new AtomicReference<>();
        PacketBatcher.send(List.of(new RawPacket(500, 0)), main::set);
        var executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> PacketBatcher.send(List.of(new RawPacket(500, 0)),
                    stream -> assertNotSame(main.get(), stream))).get();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void customCompressionProvidersKeepTheOriginalOwnershipPath() {
        CompressionProvider custom = new CompressionProvider() {
            public byte[] compress(BinaryStream packet, int level) { return packet.getBuffer(); }
            public byte[] decompress(byte[] compressed) { return compressed; }
        };
        assertFalse(PacketBatcher.supports(custom));
        assertTrue(PacketBatcher.supports(CompressionProvider.NONE));
        assertTrue(PacketBatcher.supports(CompressionProvider.ZLIB));
        assertTrue(PacketBatcher.supports(CompressionProvider.ZLIB_RAW));
        assertTrue(PacketBatcher.supports(CompressionProvider.SNAPPY));
        assertTrue(PacketBatcher.supports(CompressionProvider.NETEASE_UNKNOWN));
    }

    @Test
    void changingCodecDuringEncodingOrBetweenFlushesCannotRetainLeasedStorage() {
        for (boolean duringEncoding : new boolean[]{true, false}) {
            AtomicReference<CompressionProvider> current = new AtomicReference<>(CompressionProvider.NONE);
            List<BinaryStream> retained = new ArrayList<>();
            List<byte[]> expected = new ArrayList<>();
            CompressionProvider custom = new CompressionProvider() {
                public byte[] compress(BinaryStream input, int level) {
                    retained.add(input);
                    expected.add(input.getBuffer());
                    return input.getBufferUnsafe();
                }
                public byte[] decompress(byte[] input) { return input; }
            };
            RawPacket packet = new RawPacket(71, 9) {
                @Override public void encode() {
                    if (duringEncoding) current.set(custom);
                }
            };
            packet.isEncoded = false;
            List<DataPacket> packets = duringEncoding ? List.of(packet)
                    : List.of(new RawPacket(PacketBatcher.MAX_BATCH_BYTES, 0), packet);
            PacketBatcher.send(packets, stream -> {
                try {
                    PacketBatcher.compressBorrowed(current.get(), stream, 1);
                    if (!duringEncoding) current.set(custom);
                } catch (Exception error) { throw new AssertionError(error); }
            });
            PacketBatcher.send(List.of(new RawPacket(65536, 0)), ignored -> { });
            assertEquals(1, retained.size());
            assertArrayEquals(expected.get(0), retained.get(0).getBuffer());
        }
    }

    private static List<byte[]> collect(Collection<DataPacket> packets, boolean candidate) {
        List<byte[]> result = new ArrayList<>();
        Consumer<BinaryStream> sink = stream -> result.add(stream.getBuffer());
        if (candidate) PacketBatcher.send(packets, sink); else baseline(packets, sink);
        return result;
    }

    static void baseline(Collection<DataPacket> packets, Consumer<BinaryStream> send) {
        BinaryStream batched = new BinaryStream();
        for (DataPacket packet : packets) {
            if (packet instanceof BatchPacket) throw new IllegalArgumentException("Cannot batch BatchPacket");
            packet.tryEncode();
            byte[] bytes = packet.getBuffer();
            if (batched.getCount() + bytes.length > PacketBatcher.MAX_BATCH_BYTES) {
                send.accept(batched);
                batched = new BinaryStream();
            }
            batched.putUnsignedVarInt(bytes.length);
            batched.put(bytes);
        }
        send.accept(batched);
    }

    static class RawPacket extends DataPacket {
        RawPacket(int length, int spare) {
            byte[] data = new byte[length + spare];
            new Random(length).nextBytes(data);
            setBuffer(data);
            setCount(length);
            isEncoded = true;
        }
        @Override public byte pid() { return 1; }
        @Override public void encode() { throw new AssertionError("already encoded"); }
        @Override public void decode() { throw new AssertionError("outbound only"); }
    }
}
