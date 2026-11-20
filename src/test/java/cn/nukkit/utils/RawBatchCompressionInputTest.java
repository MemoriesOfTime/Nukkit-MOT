package cn.nukkit.utils;

import cn.nukkit.GameVersion;
import cn.nukkit.Server;
import cn.nukkit.network.CompressionProvider;
import cn.nukkit.network.protocol.LevelChunkPacket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objenesis.ObjenesisStd;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class RawBatchCompressionInputTest {
    private Field serverField;
    private Field providerField;
    private Object previousServer;
    private Object previousProvider;
    private Server server;
    private static volatile byte[] allocationSink;

    @BeforeEach
    void saveGlobals() throws Exception {
        serverField = Server.class.getDeclaredField("instance");
        serverField.setAccessible(true);
        providerField = Zlib.class.getDeclaredField("provider");
        providerField.setAccessible(true);
        previousServer = serverField.get(null);
        previousProvider = providerField.get(null);
        server = new ObjenesisStd().newInstance(Server.class);
        server.networkCompressionThreshold = 256;
        serverField.set(null, server);
        Zlib.setProvider(2);
    }

    @AfterEach
    void restoreGlobals() throws Exception {
        providerField.set(null, previousProvider);
        serverField.set(null, previousServer);
        allocationSink = null;
    }

    private static BinaryStream stream(byte[] bytes) {
        BinaryStream stream = new BinaryStream(bytes.length + 137);
        Arrays.fill(stream.getBufferUnsafe(), (byte) 0x7f);
        stream.put(bytes);
        stream.setOffset(bytes.length / 2); // Read cursor does not delimit an outgoing batch.
        return stream;
    }

    @Test
    void allStockProvidersKeepExactBytesAtThresholdAndBufferBoundaries() throws Exception {
        for (int provider = 0; provider < 3; provider++) {
            Zlib.setProvider(provider);
            for (int threshold : new int[]{0, 256, 8192}) {
                server.networkCompressionThreshold = threshold;
                for (int size : new int[]{0, 1, 127, 128, 255, 256, 257, 8191, 8192, 8193, 16383, 16384, 65536}) {
                    byte[] bytes = new byte[size];
                    new Random(size).nextBytes(bytes);
                    BinaryStream input = stream(bytes);
                    byte[] original = input.getBufferUnsafe().clone();
                    for (int level : new int[]{-1, 0, 1, 5, 7, 9}) {
                        assertArrayEquals(Zlib.deflateRaw(bytes, level),
                                CompressionProvider.ZLIB_RAW.compress(input, level),
                                "provider=" + provider + " threshold=" + threshold + " size=" + size + " level=" + level);
                        assertArrayEquals(original, input.getBufferUnsafe());
                    }
                }
            }
        }
    }

    @Test
    void framedChunksKeepExactCompressedBytesAcrossTheClientWindow() throws Exception {
        int versions = 0;
        for (GameVersion version : GameVersion.values()) {
            if (version.getProtocol() < 589 || version.getProtocol() > 2193) continue;
            versions++;
            for (int dimension : new int[]{0, 1, 2}) {
                LevelChunkPacket packet = new LevelChunkPacket();
                packet.gameVersion = version;
                packet.protocol = version.getProtocol();
                packet.chunkX = -123;
                packet.chunkZ = 456;
                packet.dimension = dimension;
                packet.subChunkCount = 4;
                packet.data = new byte[8192];
                packet.tryEncode();
                BinaryStream batch = new BinaryStream();
                byte[] payload = packet.getBuffer();
                batch.putUnsignedVarInt(payload.length);
                batch.put(payload);
                byte[] expected = Zlib.deflateRaw(batch.getBuffer(), 5);
                byte[] actual = CompressionProvider.ZLIB_RAW.compress(batch, 5);
                batch.reset();
                batch.put(new byte[payload.length + 500]);
                assertArrayEquals(expected, actual, version + " dimension=" + dimension);
            }
        }
        assertTrue(versions >= 40, "The complete supported version window must be exercised");
    }

    @Test
    void customStreamCanTransformPayloadAndSwitchProviderBeforeCompression() throws Exception {
        AtomicReference<byte[]> received = new AtomicReference<>();
        ZlibProvider custom = rawProvider(bytes -> { received.set(bytes); return new byte[]{99}; });
        int[] calls = {0};
        BinaryStream input = new BinaryStream(new byte[]{1, 2, 3}) {
            @Override public byte[] getBuffer() {
                calls[0]++;
                try { providerField.set(null, custom); }
                catch (IllegalAccessException e) { throw new AssertionError(e); }
                return new byte[]{4, 5};
            }
            @Override public byte[] getBufferUnsafe() { throw new AssertionError("custom backing accessed"); }
            @Override public int getCount() { throw new AssertionError("custom count accessed"); }
        };
        assertArrayEquals(new byte[]{99}, CompressionProvider.ZLIB_RAW.compress(input, 5));
        assertArrayEquals(new byte[]{4, 5}, received.get());
        assertEquals(1, calls[0]);
    }

    @Test
    void customProviderReceivesOwnedSnapshotEvenWhenItMutatesRetainsAndReenters() throws Exception {
        BinaryStream input = stream(new byte[]{1, 2, 3});
        AtomicReference<byte[]> retained = new AtomicReference<>();
        providerField.set(null, rawProvider(bytes -> {
            retained.set(bytes);
            Zlib.setProvider(2);
            CompressionProvider.ZLIB_RAW.compress(stream(new byte[]{7, 8}), 5);
            bytes[0] = 42;
            return bytes;
        }));
        assertArrayEquals(new byte[]{42, 2, 3}, CompressionProvider.ZLIB_RAW.compress(input, 5));
        assertArrayEquals(new byte[]{1, 2, 3}, input.getBuffer());
        input.reset();
        input.put(new byte[]{9, 9, 9});
        assertArrayEquals(new byte[]{42, 2, 3}, retained.get());
    }

    @Test
    void unusualCountsKeepPaddingAndFailureSemantics() throws Exception {
        BinaryStream input = new BinaryStream(new byte[]{1, 2, 3});
        input.setCount(6);
        assertArrayEquals(Zlib.deflateRaw(new byte[]{1, 2, 3, 0, 0, 0}, 5),
                CompressionProvider.ZLIB_RAW.compress(input, 5));
        input.setCount(-1);
        assertThrows(NegativeArraySizeException.class, () -> CompressionProvider.ZLIB_RAW.compress(input, 5));
        input.setBuffer(null);
        input.setCount(0);
        assertThrows(NullPointerException.class, () -> CompressionProvider.ZLIB_RAW.compress(input, 5));
        assertThrows(IllegalArgumentException.class,
                () -> CompressionProvider.ZLIB_RAW.compress(stream(new byte[1024]), 10));
        assertArrayEquals(Zlib.deflateRaw(new byte[]{2, 3}, 5),
                CompressionProvider.ZLIB_RAW.compress(stream(new byte[]{2, 3}), 5));
    }

    @Test
    void independentWorkersKeepOwnedOutputsAfterInputReuse() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> repeatCompression(17));
            var second = executor.submit(() -> repeatCompression(29));
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static boolean repeatCompression(int seed) throws Exception {
        byte[] bytes = new byte[8192];
        new Random(seed).nextBytes(bytes);
        byte[] expected = Zlib.deflateRaw(bytes, 5);
        for (int i = 0; i < 64; i++) {
            BinaryStream input = stream(bytes);
            byte[] actual = CompressionProvider.ZLIB_RAW.compress(input, 5);
            Arrays.fill(input.getBufferUnsafe(), (byte) seed);
            assertArrayEquals(expected, actual);
        }
        return true;
    }

    @Test
    void stockRawCompressionAvoidsOneInputSizedAllocationPerBatch() throws Exception {
        var baseBean = ManagementFactory.getThreadMXBean();
        assumeTrue(baseBean instanceof com.sun.management.ThreadMXBean);
        var bean = (com.sun.management.ThreadMXBean) baseBean;
        assumeTrue(bean.isThreadAllocatedMemorySupported() && bean.isThreadAllocatedMemoryEnabled());
        byte[] bytes = new byte[65536];
        Arrays.fill(bytes, (byte) 31);
        BinaryStream input = stream(bytes);
        Compression reference = () -> Zlib.deflateRaw(input.getBuffer(), 5);
        Compression actual = () -> CompressionProvider.ZLIB_RAW.compress(input, 5);
        for (int i = 0; i < 512; i++) {
            allocationSink = reference.run();
            allocationSink = actual.run();
        }
        long before = allocated(bean, reference);
        long after = allocated(bean, actual);
        // Allow fixed JVM/JIT allocation noise while still detecting an input-sized snapshot.
        assertTrue(before - after >= bytes.length - 1024,
                "Expected removal of input snapshot: reference=" + before + " B/op, actual=" + after + " B/op");
    }

    private static long allocated(com.sun.management.ThreadMXBean bean, Compression operation) throws Exception {
        long minimum = Long.MAX_VALUE;
        for (int round = 0; round < 3; round++) {
            long thread = Thread.currentThread().getId();
            long start = bean.getThreadAllocatedBytes(thread);
            for (int i = 0; i < 64; i++) allocationSink = operation.run();
            minimum = Math.min(minimum, (bean.getThreadAllocatedBytes(thread) - start) / 64);
        }
        return minimum;
    }

    private static ZlibProvider rawProvider(RawCompression operation) {
        return (ZlibProvider) Proxy.newProxyInstance(ZlibProvider.class.getClassLoader(),
                new Class<?>[]{ZlibProvider.class}, (proxy, method, args) -> {
                    if (method.getName().equals("deflateRaw") && args[0] instanceof byte[] bytes) {
                        return operation.run(bytes);
                    }
                    throw new AssertionError("Unexpected provider call: " + method);
                });
    }

    @FunctionalInterface private interface Compression { byte[] run() throws Exception; }
    @FunctionalInterface private interface RawCompression { byte[] run(byte[] bytes) throws Exception; }
}
