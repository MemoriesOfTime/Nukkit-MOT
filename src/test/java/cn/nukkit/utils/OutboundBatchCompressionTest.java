package cn.nukkit.utils;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.network.CompressionProvider;
import cn.nukkit.network.encryption.EncryptionUtils;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.session.RakNetPlayerSession;
import cn.nukkit.network.session.NetherNetPlayerSession;
import cn.nukkit.network.session.login.NetworkSessionState;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import org.cloudburstmc.netty.channel.raknet.RakChildChannel;
import org.cloudburstmc.netty.channel.nethernet.NetherNetChildChannel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.AtomicBoolean;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Answers.CALLS_REAL_METHODS;

/** Invokes both real transport batching methods; restores every changed global. */
class OutboundBatchCompressionTest {
    @BeforeAll static void init() { MockServer.init(); }

    static class CountingZlib implements ZlibProvider {
        private final ZlibProvider delegate = new ZlibThreadLocal();
        final AtomicInteger calls = new AtomicInteger();
        volatile int level;
        boolean failNext;
        @Override public byte[] deflateRaw(byte[] bytes, int level) throws IOException {
            calls.incrementAndGet();
            this.level = level;
            if (failNext) { failNext = false; throw new IOException("fixture compression failure"); }
            return delegate.deflateRaw(bytes, level);
        }
        public byte[] deflate(byte[] b, int l) throws IOException { return delegate.deflate(b, l); }
        public byte[] deflate(byte[][] b, int l) throws IOException { return delegate.deflate(b, l); }
        public byte[] deflateRaw(byte[][] b, int l) throws IOException { return delegate.deflateRaw(b, l); }
        public byte[] inflate(byte[] b, int max) throws IOException { return delegate.inflate(b, max); }
        public byte[] inflateRaw(byte[] b, int max) throws IOException { return delegate.inflateRaw(b, max); }
    }

    static final class Fixture implements AutoCloseable {
        final Field provider;
        final Object previous;
        final int previousLevel, previousThreshold;
        final CountingZlib compressor = new CountingZlib();
        Fixture() throws Exception {
            provider = Zlib.class.getDeclaredField("provider");
            provider.setAccessible(true);
            previous = provider.get(null);
            previousLevel = MockServer.get().networkCompressionLevel;
            previousThreshold = MockServer.get().networkCompressionThreshold;
            provider.set(null, compressor);
            MockServer.get().networkCompressionLevel = 7;
            MockServer.get().networkCompressionThreshold = 256;
        }
        public void close() throws Exception {
            provider.set(null, previous);
            MockServer.get().networkCompressionLevel = previousLevel;
            MockServer.get().networkCompressionThreshold = previousThreshold;
        }
    }

    static void field(Object object, String name, Object value) throws Exception {
        Field f = object.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(object, value);
    }

    static byte[] send(Class<?> transport, byte[] payload, int protocol,
                       CompressionProvider compression) throws Exception {
        return send(transport, payload, protocol, compression, false, null, 0);
    }

    static byte[] send(Class<?> transport, byte[] payload, int protocol,
                       CompressionProvider compression, boolean negotiated, SecretKey key,
                       long counter) throws Exception {
        Object session = mock(transport, CALLS_REAL_METHODS);
        if (session instanceof RakNetPlayerSession) {
            NetworkSessionState state = mock(NetworkSessionState.class, RETURNS_DEEP_STUBS);
            when(state.getProtocol().getGameVersion()).thenReturn(GameVersion.byProtocol(protocol, false));
            when(state.getSecurity().isCompressionInitialized()).thenReturn(negotiated);
            field(session, "state", state);
            if (key != null) {
                field(session, "encryptionKey", key);
                field(session, "encryptionCipher", EncryptionUtils.createCipher(true, true, key));
                field(session, "encryptCounter", new AtomicLong(counter));
            }
        } else {
            field(session, "compressionInitialized", negotiated);
        }
        Player player = mock(Player.class);
        player.protocol = protocol;
        when(player.getGameVersion()).thenReturn(GameVersion.byProtocol(protocol, false));
        field(session, "player", player);
        field(session, "compressionOut", compression);
        Channel channel = transport == RakNetPlayerSession.class
                ? mock(RakChildChannel.class) : mock(NetherNetChildChannel.class);
        ChannelFuture future = mock(ChannelFuture.class);
        List<byte[]> writes = new ArrayList<>();
        when(channel.writeAndFlush(any(ByteBuf.class))).thenAnswer(invocation -> {
            ByteBuf buf = invocation.getArgument(0);
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            buf.release();
            writes.add(bytes);
            return future;
        });
        field(session, "channel", channel);
        Method send = transport.getDeclaredMethod("sendPackets", BinaryStream.class);
        send.setAccessible(true);
        send.invoke(session, new BinaryStream(payload));
        if (writes.isEmpty()) return null;
        assertEquals(1, writes.size());
        byte[] wire = writes.get(0);
        if (transport == RakNetPlayerSession.class) {
            assertEquals((byte) 0xfe, wire[0]);
            wire = Arrays.copyOfRange(wire, 1, wire.length);
        }
        if (key != null) {
            wire = EncryptionUtils.createCipher(true, false, key).update(wire);
            byte[] compressed = Arrays.copyOf(wire, wire.length - 8);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(counter).array());
            digest.update(compressed);
            digest.update(key.getEncoded());
            assertArrayEquals(Arrays.copyOf(digest.digest(), 8), Arrays.copyOfRange(wire, wire.length - 8, wire.length));
            wire = compressed;
        }
        if (negotiated && protocol >= ProtocolInfo.v1_20_60) {
            assertEquals(compression.getPrefix(), wire[0]);
            wire = Arrays.copyOfRange(wire, 1, wire.length);
        }
        return wire;
    }

    static byte[] payload(int size, int salt) {
        byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) bytes[i] = (byte) ((i % 73) + salt);
        return bytes;
    }

    @Test void identicalBroadcastBatchesCompressOnceAcrossBothTransports() throws Exception {
        try (Fixture f = new Fixture()) {
            byte[] bytes = payload(1024, 1);
            for (int i = 0; i < 12; i++) {
                Class<?> transport = i % 2 == 0 ? RakNetPlayerSession.class : NetherNetPlayerSession.class;
                assertArrayEquals(bytes, CompressionProvider.ZLIB_RAW.decompress(
                        send(transport, bytes, ProtocolInfo.v1_20_0, CompressionProvider.ZLIB_RAW)));
            }
            assertEquals(1, f.compressor.calls.get(), "one compression, twelve independent deliveries");
        }
    }

    @Test void modernSmallBatchRetainsConfiguredLevel() throws Exception {
        try (Fixture f = new Fixture()) {
            byte[] bytes = payload(1024, 2);
            assertArrayEquals(bytes, CompressionProvider.ZLIB_RAW.decompress(
                    send(RakNetPlayerSession.class, bytes, ProtocolInfo.v1_26_0, CompressionProvider.ZLIB_RAW)));
            assertEquals(7, f.compressor.level, "modern batches retain the configured compression level");
        }
    }

    @Test void everyProtocolKeepsExactCompressedBytesAndRoundTrips() throws Exception {
        try (Fixture f = new Fixture()) {
            for (GameVersion version : GameVersion.values()) {
                int protocol = version.getProtocol();
                if (version.isNetEase() || protocol < 589) continue;
                for (int size : new int[]{255, 256, 1024, 16384, 65537}) {
                    byte[] bytes = payload(size, protocol);
                    byte[] expected = CompressionProvider.ZLIB_RAW.compress(new BinaryStream(bytes), 7);
                    byte[] actual = send(RakNetPlayerSession.class, bytes, protocol, CompressionProvider.ZLIB_RAW);
                    assertArrayEquals(expected, actual, version.toString());
                    assertArrayEquals(bytes, CompressionProvider.ZLIB_RAW.decompress(actual), version.toString());
                }
            }
        }
    }

    @Test void payloadProtocolLevelThresholdAndProviderNeverShareWrongBytes() throws Exception {
        try (Fixture f = new Fixture()) {
            byte[] bytes = payload(2048, 3);
            for (int protocol : new int[]{ProtocolInfo.v1_20_0, ProtocolInfo.v1_20_10})
                assertArrayEquals(bytes, CompressionProvider.ZLIB_RAW.decompress(send(RakNetPlayerSession.class,
                        bytes, protocol, CompressionProvider.ZLIB_RAW)));
            MockServer.get().networkCompressionLevel = 1;
            send(RakNetPlayerSession.class, bytes, ProtocolInfo.v1_20_0, CompressionProvider.ZLIB_RAW);
            MockServer.get().networkCompressionThreshold = 4096;
            send(RakNetPlayerSession.class, bytes, ProtocolInfo.v1_20_0, CompressionProvider.ZLIB_RAW);
            bytes[300]++;
            send(RakNetPlayerSession.class, bytes, ProtocolInfo.v1_20_0, CompressionProvider.ZLIB_RAW);
            assertEquals(5, f.compressor.calls.get());
            CountingZlib replacement = new CountingZlib();
            f.provider.set(null, replacement);
            send(RakNetPlayerSession.class, bytes, ProtocolInfo.v1_20_0, CompressionProvider.ZLIB_RAW);
            assertEquals(1, replacement.calls.get());
        }
    }

    @Test void compressionFailureIsNeverCached() throws Exception {
        try (Fixture f = new Fixture()) {
            f.compressor.failNext = true;
            byte[] bytes = payload(2048, 4);
            assertNull(send(RakNetPlayerSession.class, bytes, 589, CompressionProvider.ZLIB_RAW));
            assertNotNull(send(RakNetPlayerSession.class, bytes, 589, CompressionProvider.ZLIB_RAW));
            assertNotNull(send(RakNetPlayerSession.class, bytes, 589, CompressionProvider.ZLIB_RAW));
            assertEquals(2, f.compressor.calls.get(), "failed attempt then one successful reusable result");
        }
    }

    @Test void nonRawProvidersKeepTheirExistingBytes() throws Exception {
        try (Fixture f = new Fixture()) {
            byte[] bytes = payload(4096, 5);
            for (CompressionProvider p : new CompressionProvider[]{CompressionProvider.NONE,
                    CompressionProvider.SNAPPY, CompressionProvider.ZLIB}) {
                byte[] expected = p.compress(new BinaryStream(bytes), 7);
                assertArrayEquals(expected, send(RakNetPlayerSession.class, bytes, 589, p));
            }
        }
    }

    @Test void hashCollisionsNeverReuseDifferentPayloads() throws Exception {
        try (Fixture f = new Fixture()) {
            MockServer.get().networkCompressionThreshold = 0;
            byte[] first = {0, 31};
            byte[] second = {1, 0};
            assertEquals(Arrays.hashCode(first), Arrays.hashCode(second));
            assertArrayEquals(first, CompressionProvider.ZLIB_RAW.decompress(
                    send(RakNetPlayerSession.class, first, 589, CompressionProvider.ZLIB_RAW)));
            assertArrayEquals(second, CompressionProvider.ZLIB_RAW.decompress(
                    send(RakNetPlayerSession.class, second, 589, CompressionProvider.ZLIB_RAW)));
            assertEquals(2, f.compressor.calls.get());
        }
    }

    @Test void configuredLowLevelsAndLargeBatchesRemainUntouched() throws Exception {
        try (Fixture f = new Fixture()) {
            for (int level : new int[]{0, 1, 2, 3, -1, 7}) {
                MockServer.get().networkCompressionLevel = level;
                byte[] small = payload(1024, level + 11);
                byte[] actual = send(RakNetPlayerSession.class, small, 924, CompressionProvider.ZLIB_RAW);
                int expected = level;
                assertArrayEquals(CompressionProvider.ZLIB_RAW.compress(new BinaryStream(small), expected), actual);
                byte[] large = payload(65537, level + 17);
                actual = send(RakNetPlayerSession.class, large, 924, CompressionProvider.ZLIB_RAW);
                assertArrayEquals(CompressionProvider.ZLIB_RAW.compress(new BinaryStream(large), level), actual);
            }
        }
    }

    @Test void simultaneousRecipientsShareOneCompression() throws Exception {
        try (Fixture f = new Fixture()) {
            byte[] bytes = payload(4096, 29);
            var pool = java.util.concurrent.Executors.newFixedThreadPool(12);
            var start = new java.util.concurrent.CountDownLatch(1);
            try {
                var jobs = new ArrayList<java.util.concurrent.Future<byte[]>>();
                for (int i = 0; i < 12; i++) jobs.add(pool.submit(() -> {
                    start.await();
                    return send(RakNetPlayerSession.class, bytes, 589, CompressionProvider.ZLIB_RAW);
                }));
                start.countDown();
                for (var job : jobs) assertArrayEquals(bytes, CompressionProvider.ZLIB_RAW.decompress(
                        job.get(20, java.util.concurrent.TimeUnit.SECONDS)));
                assertEquals(1, f.compressor.calls.get());
            } finally {
                pool.shutdownNow();
                assertTrue(pool.awaitTermination(20, java.util.concurrent.TimeUnit.SECONDS));
            }
        }
    }

    @Test void lowMemoryProviderKeepsOriginalBytesAndReusesCompression() throws Exception {
        try (Fixture f = new Fixture()) {
            class CountingLowMemory extends ZlibSingleThreadLowMem {
                int calls;
                public byte[] deflateRaw(byte[] b, int level) throws IOException {
                    calls++;
                    return super.deflateRaw(b, level);
                }
            }
            var low = new CountingLowMemory();
            f.provider.set(null, low);
            byte[] bytes = payload(1024, 37);
            byte[] expected = CompressionProvider.ZLIB_RAW.compress(new BinaryStream(bytes), 7);
            for (int i = 0; i < 3; i++) assertArrayEquals(expected,
                    send(RakNetPlayerSession.class, bytes, 924, CompressionProvider.ZLIB_RAW));
            assertEquals(2, low.calls);
        }
    }

    @Test void negotiatedPrefixesKeepOldCompressionBodyOnBothTransports() throws Exception {
        try (Fixture f = new Fixture()) {
            byte[] bytes = payload(1024, 43);
            for (Class<?> transport : new Class<?>[]{RakNetPlayerSession.class, NetherNetPlayerSession.class})
                for (int protocol : new int[]{589, ProtocolInfo.v1_20_60})
                    for (CompressionProvider p : new CompressionProvider[]{CompressionProvider.NONE,
                            CompressionProvider.SNAPPY, CompressionProvider.ZLIB_RAW})
                        assertArrayEquals(p.compress(new BinaryStream(bytes), 7),
                                send(transport, bytes, protocol, p, true, null, 0));
        }
    }

    @Test void sharedBodyStillGetsEachSessionsCipherAndChecksumCounter() throws Exception {
        try (Fixture f = new Fixture()) {
            byte[] bytes = payload(2048, 47);
            for (int protocol : new int[]{589, ProtocolInfo.v1_20_60, 924}) {
                int before = f.compressor.calls.get();
                for (int salt : new int[]{1, 2, 3}) {
                    SecretKey key = new SecretKeySpec(payload(32, salt), "AES");
                    assertArrayEquals(bytes, CompressionProvider.ZLIB_RAW.decompress(
                            send(RakNetPlayerSession.class, bytes, protocol, CompressionProvider.ZLIB_RAW,
                                    true, key, salt * 17)));
                }
                assertEquals(before + 1, f.compressor.calls.get(), "cipher/counter differ, compressed body is shared");
            }
        }
    }

    @Test void expiredEntryCompressesAgainWithoutDroppingDelivery() throws Exception {
        try (Fixture f = new Fixture()) {
            byte[] bytes = payload(2048, 53);
            assertNotNull(send(RakNetPlayerSession.class, bytes, 589, CompressionProvider.ZLIB_RAW));
            assertNotNull(send(RakNetPlayerSession.class, bytes, 589, CompressionProvider.ZLIB_RAW));
            assertEquals(1, f.compressor.calls.get());
            Thread.sleep(1100);
            assertNotNull(send(RakNetPlayerSession.class, bytes, 589, CompressionProvider.ZLIB_RAW));
            assertEquals(2, f.compressor.calls.get());
        }
    }

    @Test void equalSamplesStillRequireFullPayloadEquality() throws Exception {
        try (Fixture f = new Fixture()) {
            byte[] first = payload(1024, 61);
            byte[] second = first.clone();
            second[200]++; // Outside first/middle/last samples used to choose the slot.
            assertArrayEquals(first, CompressionProvider.ZLIB_RAW.decompress(
                    send(RakNetPlayerSession.class, first, 589, CompressionProvider.ZLIB_RAW)));
            assertArrayEquals(second, CompressionProvider.ZLIB_RAW.decompress(
                    send(RakNetPlayerSession.class, second, 589, CompressionProvider.ZLIB_RAW)));
            assertEquals(2, f.compressor.calls.get());
        }
    }

    @Test void cacheHitAvoidsFullPayloadSnapshotAllocation() throws Exception {
        try (Fixture f = new Fixture()) {
            var bean = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
            assertTrue(bean.isThreadAllocatedMemorySupported());
            bean.setThreadAllocatedMemoryEnabled(true);
            byte[] bytes = payload(65536, 71);
            BinaryStream batch = new BinaryStream(bytes);
            byte[] compressed = OutboundBatchCompression.compress(CompressionProvider.ZLIB_RAW, batch, 7, 589);
            assertArrayEquals(bytes, CompressionProvider.ZLIB_RAW.decompress(compressed));
            for (int i = 0; i < 256; i++) assertSame(compressed,
                    OutboundBatchCompression.compress(CompressionProvider.ZLIB_RAW, batch, 7, 589));
            long thread = Thread.currentThread().getId();
            long before = bean.getThreadAllocatedBytes(thread);
            int hits = 1024;
            for (int i = 0; i < hits; i++) assertSame(compressed,
                    OutboundBatchCompression.compress(CompressionProvider.ZLIB_RAW, batch, 7, 589));
            double allocatedPerHit = (double) (bean.getThreadAllocatedBytes(thread) - before) / hits;
            System.out.println("P06_ALLOCATION_HIT_BYTES=" + allocatedPerHit);
            assertEquals(1, f.compressor.calls.get(), "cache hits must not run the backend");
            assertArrayEquals(bytes, CompressionProvider.ZLIB_RAW.decompress(compressed));
            assertTrue(allocatedPerHit < 4096, "cache-hit allocation was " + allocatedPerHit + " bytes/hit");
        }
    }

    @Test void plainUnusedCapacitySuffixDoesNotChangeCachedLogicalRange() throws Exception {
        try (Fixture f = new Fixture()) {
            byte[] bytes = payload(1024, 73);
            BinaryStream batch = new BinaryStream(4096);
            batch.put(bytes);
            byte[] first = OutboundBatchCompression.compress(CompressionProvider.ZLIB_RAW, batch, 7, 589);
            Arrays.fill(batch.getBufferUnsafe(), batch.getCount(), 4096, (byte) 99);
            assertSame(first, OutboundBatchCompression.compress(CompressionProvider.ZLIB_RAW, batch, 7, 589));
            assertArrayEquals(bytes, CompressionProvider.ZLIB_RAW.decompress(first));
            assertEquals(1, f.compressor.calls.get());
        }
    }

    @Test void plainLogicalCountChangesRequireNewCompression() throws Exception {
        try (Fixture f = new Fixture()) {
            byte[] bytes = payload(1024, 75);
            BinaryStream batch = new BinaryStream(4096);
            batch.put(bytes);
            OutboundBatchCompression.compress(CompressionProvider.ZLIB_RAW, batch, 7, 589);
            batch.setCount(1023);
            byte[] shorter = OutboundBatchCompression.compress(CompressionProvider.ZLIB_RAW, batch, 7, 589);
            assertArrayEquals(Arrays.copyOf(bytes, 1023), CompressionProvider.ZLIB_RAW.decompress(shorter));
            assertEquals(2, f.compressor.calls.get());
        }
    }

    @Test void plainOriginalMutationOutsideSamplesCannotChangeOwnedCachedInput() throws Exception {
        try (Fixture f = new Fixture()) {
            byte[] original = payload(4096, 77);
            byte[] before = original.clone();
            BinaryStream batch = new BinaryStream(original);
            byte[] first = OutboundBatchCompression.compress(CompressionProvider.ZLIB_RAW, batch, 7, 589);
            original[200]++;
            byte[] second = OutboundBatchCompression.compress(CompressionProvider.ZLIB_RAW, batch, 7, 589);
            assertArrayEquals(before, CompressionProvider.ZLIB_RAW.decompress(first));
            assertArrayEquals(original, CompressionProvider.ZLIB_RAW.decompress(second));
            assertEquals(2, f.compressor.calls.get());
        }
    }

    @Test void countPastCapacityRetainsZeroPaddingSnapshotSemantics() throws Exception {
        try (Fixture f = new Fixture()) {
            byte[] original = payload(64, 79);
            BinaryStream batch = new BinaryStream(original);
            batch.setCount(256);
            byte[] padded = Arrays.copyOf(original, 256);
            byte[] first = OutboundBatchCompression.compress(CompressionProvider.ZLIB_RAW, batch, 7, 589);
            assertArrayEquals(padded, CompressionProvider.ZLIB_RAW.decompress(first));
            assertSame(first, OutboundBatchCompression.compress(CompressionProvider.ZLIB_RAW, batch, 7, 589));
            assertEquals(1, f.compressor.calls.get());
        }
    }

    @Test void invalidPlainSnapshotPreservesOriginalExceptionTypes() throws Exception {
        try (Fixture f = new Fixture()) {
            for (int state = 0; state < 3; state++) {
                BinaryStream batch = new BinaryStream(payload(32, 81));
                if (state == 0) batch.setCount(-1);
                else { batch.setBuffer(null); if (state == 1) batch.setCount(0); }
                RuntimeException expected = assertThrows(RuntimeException.class,
                        () -> CompressionProvider.ZLIB_RAW.compress(batch, 7));
                RuntimeException actual = assertThrows(RuntimeException.class,
                        () -> OutboundBatchCompression.compress(CompressionProvider.ZLIB_RAW, batch, 7, 589));
                assertEquals(expected.getClass(), actual.getClass());
            }
            assertEquals(0, f.compressor.calls.get());
        }
    }

    @Test void virtualSnapshotRunsOncePerInvocationAndOverridesRawView() throws Exception {
        try (Fixture f = new Fixture()) {
            AtomicInteger snapshots = new AtomicInteger();
            byte[] virtual = payload(1024, 83);
            BinaryStream batch = new BinaryStream(payload(64, 84)) {
                @Override public byte[] getBuffer() { snapshots.incrementAndGet(); return virtual.clone(); }
                @Override public byte[] getBufferUnsafe() { throw new AssertionError("virtual stream raw view must not be used"); }
            };
            byte[] first = OutboundBatchCompression.compress(CompressionProvider.ZLIB_RAW, batch, 7, 589);
            assertSame(first, OutboundBatchCompression.compress(CompressionProvider.ZLIB_RAW, batch, 7, 589));
            assertArrayEquals(virtual, CompressionProvider.ZLIB_RAW.decompress(first));
            assertEquals(2, snapshots.get());
            assertEquals(1, f.compressor.calls.get());
        }
    }

    @Test void providerMutationOfOriginalBatchCannotCorruptOwnedMissSnapshot() throws Exception {
        try (Fixture f = new Fixture()) {
            byte[] original = payload(4096, 85);
            BinaryStream batch = new BinaryStream(original);
            CountingZlib reentrant = new CountingZlib() {
                @Override public byte[] deflateRaw(byte[] bytes, int level) throws IOException {
                    original[200]++;
                    return super.deflateRaw(bytes, level);
                }
            };
            f.provider.set(null, reentrant);
            byte[] beforeFirst = original.clone();
            byte[] first = OutboundBatchCompression.compress(CompressionProvider.ZLIB_RAW, batch, 7, 589);
            assertArrayEquals(beforeFirst, CompressionProvider.ZLIB_RAW.decompress(first));
            byte[] beforeSecond = original.clone();
            byte[] second = OutboundBatchCompression.compress(CompressionProvider.ZLIB_RAW, batch, 7, 589);
            assertArrayEquals(beforeSecond, CompressionProvider.ZLIB_RAW.decompress(second));
            assertArrayEquals(beforeFirst, CompressionProvider.ZLIB_RAW.decompress(first));
            assertEquals(2, reentrant.calls.get());
        }
    }

    @Test void mutableVirtualSnapshotMustBeOwnedBeforeProviderCallback() throws Exception {
        try (Fixture f = new Fixture()) {
            byte[] virtual = payload(4096, 87);
            AtomicInteger snapshots = new AtomicInteger();
            BinaryStream batch = new BinaryStream(payload(64, 88)) {
                @Override public byte[] getBuffer() { snapshots.incrementAndGet(); return virtual; }
            };
            CountingZlib mutating = new CountingZlib() {
                @Override public byte[] deflateRaw(byte[] bytes, int level) throws IOException {
                    virtual[200]++;
                    return super.deflateRaw(bytes, level);
                }
            };
            f.provider.set(null, mutating);
            byte[] expected = virtual.clone();
            byte[] first = OutboundBatchCompression.compress(CompressionProvider.ZLIB_RAW, batch, 7, 589);
            assertArrayEquals(expected, CompressionProvider.ZLIB_RAW.decompress(first));
            expected = virtual.clone();
            byte[] second = OutboundBatchCompression.compress(CompressionProvider.ZLIB_RAW, batch, 7, 589);
            assertArrayEquals(expected, CompressionProvider.ZLIB_RAW.decompress(second));
            assertEquals(2, snapshots.get());
            assertEquals(2, mutating.calls.get());
        }
    }

    @Test void mutableVirtualSnapshotExternalMutationMustMissOldEntry() throws Exception {
        try (Fixture f = new Fixture()) {
            byte[] virtual = payload(4096, 89);
            BinaryStream batch = new BinaryStream(payload(64, 90)) {
                @Override public byte[] getBuffer() { return virtual; }
            };
            byte[] expected = virtual.clone();
            byte[] first = OutboundBatchCompression.compress(CompressionProvider.ZLIB_RAW, batch, 7, 589);
            virtual[200]++;
            byte[] second = OutboundBatchCompression.compress(CompressionProvider.ZLIB_RAW, batch, 7, 589);
            assertArrayEquals(expected, CompressionProvider.ZLIB_RAW.decompress(first));
            assertArrayEquals(virtual, CompressionProvider.ZLIB_RAW.decompress(second));
            assertEquals(2, f.compressor.calls.get());
        }
    }

    @Test void lowMemoryProviderReadsOneVirtualSnapshotLikeOriginalCompressor() throws Exception {
        try (Fixture f = new Fixture()) {
            f.provider.set(null, new ZlibSingleThreadLowMem());
            AtomicInteger snapshots = new AtomicInteger();
            BinaryStream batch = new BinaryStream(payload(64, 92)) {
                @Override public byte[] getBuffer() { return payload(1024, 93 + snapshots.incrementAndGet()); }
            };
            byte[] expected = CompressionProvider.ZLIB_RAW.compress(batch, 7);
            assertEquals(1, snapshots.get());
            snapshots.set(0);
            byte[] actual = OutboundBatchCompression.compress(CompressionProvider.ZLIB_RAW, batch, 7, 924);
            assertEquals(1, snapshots.get(), "provider1 must preserve original compressor snapshot count");
            assertArrayEquals(expected, actual);
        }
    }

    static final class FlightZlib extends CountingZlib {
        final ZlibProvider raw = new ZlibThreadLocal();
        final int marker;
        final boolean failFirst;
        final boolean failWithError;
        final AtomicIntegerArray perKey = new AtomicIntegerArray(3);
        final AtomicReferenceArray<byte[]> inputs = new AtomicReferenceArray<>(3);
        final CountDownLatch[] entered = {new CountDownLatch(1), new CountDownLatch(1), new CountDownLatch(1)};
        final CountDownLatch[] release = {new CountDownLatch(1), new CountDownLatch(1), new CountDownLatch(1)};
        FlightZlib(byte[] first, boolean failFirst) { this(first, failFirst, false); }
        FlightZlib(byte[] first, boolean failFirst, boolean failWithError) {
            marker = first[200] & 255;
            this.failFirst = failFirst;
            this.failWithError = failWithError;
        }
        @Override public byte[] deflateRaw(byte[] bytes, int level) throws IOException {
            int key = ((bytes[200] & 255) - marker) & 255;
            if (key >= 3) throw new IOException("unexpected fixture key");
            int call = calls.incrementAndGet();
            perKey.incrementAndGet(key);
            inputs.compareAndSet(key, null, bytes);
            entered[key].countDown();
            try {
                if (!release[key].await(3, TimeUnit.SECONDS)) throw new IOException("flight fixture timed out");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException(interrupted);
            }
            if (failFirst && call == 1) {
                if (failWithError) throw new AssertionError("injected leader error");
                throw new IOException("injected leader failure");
            }
            return raw.deflateRaw(bytes, level);
        }
        void releaseAll() { for (CountDownLatch gate : release) gate.countDown(); }
    }

    static ExecutorService flightPool(int threads) {
        return Executors.newFixedThreadPool(threads, task -> {
            Thread thread = new Thread(task, "p06-flight-proof");
            thread.setDaemon(true);
            return thread;
        });
    }

    static Future<byte[]> compressOn(ExecutorService pool, byte[] input) {
        return pool.submit(() -> OutboundBatchCompression.compress(
                CompressionProvider.ZLIB_RAW, new BinaryStream(input), 7, 589));
    }

    static List<Future<byte[]>> waiters(ExecutorService pool, byte[] input, int count,
                                      AtomicReferenceArray<Thread> threads) {
        List<Future<byte[]>> outputs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            final int worker = i;
            outputs.add(pool.submit(() -> {
                threads.set(worker, Thread.currentThread());
                return OutboundBatchCompression.compress(
                        CompressionProvider.ZLIB_RAW, new BinaryStream(input), 7, 589);
            }));
        }
        return outputs;
    }

    static void awaitWaiters(AtomicReferenceArray<Thread> threads) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (System.nanoTime() < deadline) {
            boolean waiting = true;
            for (int i = 0; i < threads.length(); i++) {
                Thread thread = threads.get(i);
                Thread.State state = thread == null ? Thread.State.NEW : thread.getState();
                waiting &= state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING
                        || state == Thread.State.BLOCKED;
            }
            if (waiting) return;
            Thread.sleep(1);
        }
        fail("all callers must be waiting behind an active identical flight before it completes");
    }

    static void stopFlights(ExecutorService pool, FlightZlib backend) throws Exception {
        backend.releaseAll();
        pool.shutdownNow();
        assertTrue(pool.awaitTermination(3, TimeUnit.SECONDS));
    }

    static Object value(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    static Object slotWithFlight(byte[] input) throws Exception {
        Field slots = OutboundBatchCompression.class.getDeclaredField("SLOTS");
        slots.setAccessible(true);
        for (Object slot : (Object[]) slots.get(null)) {
            synchronized (slot) {
                for (Object flight = value(slot, "flights"); flight != null; flight = value(flight, "next")) {
                    if (Arrays.equals(input, (byte[]) value(value(flight, "key"), "input"))) return slot;
                }
            }
        }
        throw new AssertionError("expected active flight missing");
    }

    static List<byte[]> flightInputs(Object slot) throws Exception {
        List<byte[]> inputs = new ArrayList<>();
        synchronized (slot) {
            for (Object flight = value(slot, "flights"); flight != null; flight = value(flight, "next")) {
                inputs.add((byte[]) value(value(flight, "key"), "input"));
            }
        }
        return inputs;
    }

    static final class GatedFuture extends CompletableFuture<byte[]> {
        final CountDownLatch joined = new CountDownLatch(1);
        final CountDownLatch resume = new CountDownLatch(1);
        @Override public byte[] join() {
            byte[] output = super.join();
            joined.countDown();
            try {
                if (!resume.await(3, TimeUnit.SECONDS)) throw new AssertionError("waiter resume timed out");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new CompletionException(interrupted);
            }
            return output;
        }
    }

    @Test void capturedFlightResultSurvivesCollidingCacheEvictionBeforeWaiterReturns() throws Exception {
        try (Fixture f = new Fixture()) {
            byte[] first = payload(4096, 121);
            byte[] foreign = first.clone(); foreign[200]++;
            FlightZlib backend = new FlightZlib(first, false);
            f.provider.set(null, backend);
            ExecutorService pool = flightPool(3);
            GatedFuture gated = new GatedFuture();
            try {
                Future<byte[]> leader = compressOn(pool, first);
                assertTrue(backend.entered[0].await(1, TimeUnit.SECONDS));
                Future<byte[]> foreignOutput = compressOn(pool, foreign);
                assertTrue(backend.entered[1].await(1, TimeUnit.SECONDS));
                Object slot = slotWithFlight(first);
                synchronized (slot) {
                    boolean replaced = false;
                    for (Object flight = value(slot, "flights"); flight != null; flight = value(flight, "next")) {
                        if (Arrays.equals(first, (byte[]) value(value(flight, "key"), "input"))) {
                            Field result = flight.getClass().getDeclaredField("result");
                            result.setAccessible(true);
                            result.set(flight, gated); // Fixture only: scheduler pause after the waiter captures output.
                            replaced = true;
                            break;
                        }
                    }
                    assertTrue(replaced);
                }
                // Executor submission publishes the replacement before the waiter can observe the flight.
                AtomicReferenceArray<Thread> threads = new AtomicReferenceArray<>(1);
                Future<byte[]> waiter = waiters(pool, first, 1, threads).get(0);
                awaitWaiters(threads);
                backend.release[0].countDown();
                assertTrue(gated.joined.await(1, TimeUnit.SECONDS));
                byte[] shared = leader.get(5, TimeUnit.SECONDS);
                backend.release[1].countDown();
                assertArrayEquals(foreign, CompressionProvider.ZLIB_RAW.decompress(
                        foreignOutput.get(5, TimeUnit.SECONDS)));
                synchronized (slot) {
                    assertArrayEquals(foreign, (byte[]) value(value(slot, "entry"), "input"));
                }
                gated.resume.countDown();
                assertSame(shared, waiter.get(5, TimeUnit.SECONDS), "waiter must use its completed flight, not reread the evicted entry");
                assertArrayEquals(first, CompressionProvider.ZLIB_RAW.decompress(shared));
                assertArrayEquals(backend.raw.deflateRaw(first, 7), shared);
                assertEquals(2, backend.calls.get());
            } finally {
                gated.resume.countDown();
                stopFlights(pool, backend);
            }
        }
    }

    @Test void twoFlightsBoundSameSlotAndThirdDistinctKeyBypassesWithoutRetention() throws Exception {
        try (Fixture f = new Fixture()) {
            byte[][] inputs = {payload(64 * 1024, 119), null, null};
            inputs[1] = inputs[0].clone(); inputs[1][200]++;
            inputs[2] = inputs[0].clone(); inputs[2][200] += 2;
            byte[][] before = {inputs[0].clone(), inputs[1].clone(), inputs[2].clone()};
            FlightZlib backend = new FlightZlib(inputs[0], false);
            f.provider.set(null, backend);
            ExecutorService pool = flightPool(3);
            try {
                List<Future<byte[]>> outputs = new ArrayList<>();
                outputs.add(compressOn(pool, inputs[0]));
                assertTrue(backend.entered[0].await(1, TimeUnit.SECONDS));
                outputs.add(compressOn(pool, inputs[1]));
                assertTrue(backend.entered[1].await(1, TimeUnit.SECONDS));
                Object slot = slotWithFlight(before[0]);
                List<byte[]> owned = flightInputs(slot);
                assertEquals(2, owned.size());
                assertEquals(2L * 64 * 1024, owned.stream().mapToLong(bytes -> bytes.length).sum());
                outputs.add(compressOn(pool, inputs[2]));
                assertTrue(backend.entered[2].await(1, TimeUnit.SECONDS), "third key must bypass the full flight list");
                assertEquals(2, flightInputs(slot).size(), "bypass must not retain a third flight snapshot");
                for (int i = 0; i < 3; i++) {
                    assertNotSame(inputs[i], backend.inputs.get(i));
                    inputs[i][1000]++;
                    assertArrayEquals(before[i], backend.inputs.get(i), "backend must own the complete pre-callback input");
                }
                backend.release[2].countDown();
                assertArrayEquals(before[2], CompressionProvider.ZLIB_RAW.decompress(
                        outputs.get(2).get(5, TimeUnit.SECONDS)));
                synchronized (slot) {
                    Object entry = value(slot, "entry");
                    if (entry != null) assertFalse(Arrays.equals(before[2], (byte[]) value(entry, "input")),
                            "uncached third-key bypass must not publish a cache entry");
                }
                assertEquals(2, flightInputs(slot).size());
                backend.releaseAll();
                for (int i = 0; i < 2; i++) assertArrayEquals(before[i], CompressionProvider.ZLIB_RAW.decompress(
                        outputs.get(i).get(5, TimeUnit.SECONDS)));
                assertEquals(3, backend.calls.get());
                assertTrue(flightInputs(slot).isEmpty(), "all completed flights must unlink");
            } finally { stopFlights(pool, backend); }
        }
    }

    @Test void identicalWaitersShareCompressionDuringForeignCollidingFlight() throws Exception {
        try (Fixture f = new Fixture()) {
            byte[] first = payload(64 * 1024, 115);
            byte[] foreign = first.clone();
            foreign[200]++;
            FlightZlib backend = new FlightZlib(first, false);
            f.provider.set(null, backend);
            ExecutorService pool = flightPool(8);
            try {
                Future<byte[]> foreignOutput = compressOn(pool, foreign);
                assertTrue(backend.entered[1].await(1, TimeUnit.SECONDS));
                Future<byte[]> leader = compressOn(pool, first);
                assertTrue(backend.entered[0].await(1, TimeUnit.SECONDS), "second distinct flight must start");
                AtomicReferenceArray<Thread> threads = new AtomicReferenceArray<>(6);
                List<Future<byte[]>> outputs = waiters(pool, first, 6, threads);
                awaitWaiters(threads);
                assertEquals(2, backend.calls.get());
                backend.releaseAll();
                byte[] shared = leader.get(5, TimeUnit.SECONDS);
                assertArrayEquals(first, CompressionProvider.ZLIB_RAW.decompress(shared));
                for (Future<byte[]> output : outputs) assertSame(shared, output.get(5, TimeUnit.SECONDS));
                assertArrayEquals(foreign, CompressionProvider.ZLIB_RAW.decompress(
                        foreignOutput.get(5, TimeUnit.SECONDS)));
                assertEquals(1, backend.perKey.get(0));
                assertEquals(1, backend.perKey.get(1));
            } finally { stopFlights(pool, backend); }
        }
    }

    @Test void failedFlightRetriesOnceForWaitersWithoutLosingAnyDelivery() throws Exception {
        try (Fixture f = new Fixture()) {
            byte[] input = payload(4096, 117);
            FlightZlib backend = new FlightZlib(input, true);
            f.provider.set(null, backend);
            ExecutorService pool = flightPool(8);
            try {
                Future<byte[]> leader = compressOn(pool, input);
                assertTrue(backend.entered[0].await(1, TimeUnit.SECONDS));
                AtomicReferenceArray<Thread> threads = new AtomicReferenceArray<>(7);
                List<Future<byte[]>> outputs = waiters(pool, input, 7, threads);
                awaitWaiters(threads);
                assertEquals(1, backend.calls.get());
                backend.releaseAll();
                ExecutionException failure = assertThrows(ExecutionException.class,
                        () -> leader.get(5, TimeUnit.SECONDS));
                assertInstanceOf(IOException.class, failure.getCause());
                byte[] shared = outputs.get(0).get(5, TimeUnit.SECONDS);
                for (Future<byte[]> output : outputs) assertSame(shared, output.get(5, TimeUnit.SECONDS));
                assertArrayEquals(input, CompressionProvider.ZLIB_RAW.decompress(shared));
                byte[] retried = OutboundBatchCompression.compress(
                        CompressionProvider.ZLIB_RAW, new BinaryStream(input), 7, 589);
                assertSame(shared, retried); // 9 attempts: 1 failure and 8 verified deliveries.
                assertEquals(2, backend.calls.get());
            } finally { stopFlights(pool, backend); }
        }
    }

    @Test void failedLeaderErrorUnlinksFlightAndWaitersRetryOnce() throws Exception {
        try (Fixture f = new Fixture()) {
            byte[] input = payload(4096, 123);
            FlightZlib backend = new FlightZlib(input, true, true);
            f.provider.set(null, backend);
            ExecutorService pool = flightPool(8);
            try {
                Future<byte[]> leader = compressOn(pool, input);
                assertTrue(backend.entered[0].await(1, TimeUnit.SECONDS));
                Object slot = slotWithFlight(input);
                AtomicReferenceArray<Thread> threads = new AtomicReferenceArray<>(7);
                List<Future<byte[]>> outputs = waiters(pool, input, 7, threads);
                awaitWaiters(threads);
                assertEquals(1, backend.calls.get());
                backend.releaseAll();
                ExecutionException failure = assertThrows(ExecutionException.class,
                        () -> leader.get(5, TimeUnit.SECONDS));
                assertInstanceOf(AssertionError.class, failure.getCause());
                assertEquals("injected leader error", failure.getCause().getMessage());
                byte[] shared = outputs.get(0).get(5, TimeUnit.SECONDS);
                for (Future<byte[]> output : outputs) assertSame(shared, output.get(5, TimeUnit.SECONDS));
                assertArrayEquals(input, CompressionProvider.ZLIB_RAW.decompress(shared));
                assertSame(shared, OutboundBatchCompression.compress(
                        CompressionProvider.ZLIB_RAW, new BinaryStream(input), 7, 589));
                assertEquals(2, backend.calls.get());
                assertTrue(flightInputs(slot).isEmpty(), "failed and retry flights must both unlink");
            } finally { stopFlights(pool, backend); }
        }
    }

    @Test void interruptedWaiterRetainsFlagAndReleasesProviderFence() throws Exception {
        try (Fixture f = new Fixture()) {
            byte[] input = payload(4096, 125);
            FlightZlib backend = new FlightZlib(input, false);
            f.provider.set(null, backend);
            ExecutorService pool = flightPool(2);
            AtomicBoolean interruptedAfterJoin = new AtomicBoolean();
            try {
                Future<byte[]> leader = compressOn(pool, input);
                assertTrue(backend.entered[0].await(1, TimeUnit.SECONDS));
                Object slot = slotWithFlight(input);
                AtomicReferenceArray<Thread> threads = new AtomicReferenceArray<>(1);
                Future<byte[]> waiter = pool.submit(() -> {
                    threads.set(0, Thread.currentThread());
                    byte[] output = OutboundBatchCompression.compress(
                            CompressionProvider.ZLIB_RAW, new BinaryStream(input), 7, 589);
                    interruptedAfterJoin.set(Thread.currentThread().isInterrupted());
                    return output;
                });
                awaitWaiters(threads);
                threads.get(0).interrupt();
                backend.releaseAll();
                byte[] shared = leader.get(5, TimeUnit.SECONDS);
                assertSame(shared, waiter.get(5, TimeUnit.SECONDS));
                assertTrue(interruptedAfterJoin.get(), "uninterruptible delivery wait must retain caller interrupt status");
                assertArrayEquals(input, CompressionProvider.ZLIB_RAW.decompress(shared));
                assertEquals(1, backend.calls.get());
                assertTrue(flightInputs(slot).isEmpty());
                // A leaked read fence would prevent this bounded write-side provider switch.
                pool.submit(() -> { Zlib.setProvider(2); return null; }).get(3, TimeUnit.SECONDS);
            } finally { stopFlights(pool, backend); }
        }
    }

    @Test void differentInputsInOneSlotCompressInParallel() throws Exception {
        try (Fixture f = new Fixture()) {
            byte[] first = payload(64 * 1024, 111);
            byte[] second = first.clone();
            second[200]++;
            Method hash = OutboundBatchCompression.class.getDeclaredMethod("slotHash", byte[].class, int.class);
            hash.setAccessible(true);
            assertEquals(hash.invoke(null, first, first.length), hash.invoke(null, second, second.length));
            CountDownLatch entered = new CountDownLatch(2);
            CountDownLatch release = new CountDownLatch(1);
            CountingZlib backend = new CountingZlib() {
                @Override public byte[] deflateRaw(byte[] bytes, int level) throws IOException {
                    entered.countDown();
                    try {
                        if (!release.await(3, TimeUnit.SECONDS)) throw new IOException("compression gate timed out");
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IOException(interrupted);
                    }
                    return super.deflateRaw(bytes, level);
                }
            };
            f.provider.set(null, backend);
            ExecutorService pool = Executors.newFixedThreadPool(2, task -> {
                Thread thread = new Thread(task, "p06-colliding-slot");
                thread.setDaemon(true);
                return thread;
            });
            try {
                var a = pool.submit(() -> OutboundBatchCompression.compress(
                        CompressionProvider.ZLIB_RAW, new BinaryStream(first), 7, 589));
                var b = pool.submit(() -> OutboundBatchCompression.compress(
                        CompressionProvider.ZLIB_RAW, new BinaryStream(second), 7, 589));
                assertTrue(entered.await(1, TimeUnit.SECONDS),
                        "different full inputs in the same slot must enter the backend before either completes");
                release.countDown();
                byte[] aOutput = a.get(5, TimeUnit.SECONDS), bOutput = b.get(5, TimeUnit.SECONDS);
                assertArrayEquals(first, CompressionProvider.ZLIB_RAW.decompress(aOutput));
                assertArrayEquals(second, CompressionProvider.ZLIB_RAW.decompress(bOutput));
                ZlibProvider original = new ZlibThreadLocal();
                assertArrayEquals(original.deflateRaw(first, 7), aOutput);
                assertArrayEquals(original.deflateRaw(second, 7), bOutput);
                assertEquals(2, backend.calls.get());
            } finally {
                release.countDown();
                pool.shutdownNow();
                assertTrue(pool.awaitTermination(3, TimeUnit.SECONDS));
            }
        }
    }

    @Test void virtualSnapshotCanSwitchProviderOutsideReadFence() throws Exception {
        try (Fixture f = new Fixture()) {
            byte[] virtual = payload(1024, 95);
            AtomicInteger snapshots = new AtomicInteger();
            BinaryStream batch = new BinaryStream(payload(64, 96)) {
                @Override public byte[] getBuffer() {
                    snapshots.incrementAndGet();
                    Zlib.setProvider(2);
                    return virtual.clone();
                }
            };
            var output = new java.util.concurrent.atomic.AtomicReference<byte[]>();
            var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
            Thread worker = new Thread(() -> {
                try { output.set(OutboundBatchCompression.compress(CompressionProvider.ZLIB_RAW, batch, 7, 589)); }
                catch (Throwable error) { failure.set(error); }
            }, "p06-virtual-provider-switch");
            worker.setDaemon(true);
            worker.start();
            worker.join(3000);
            assertFalse(worker.isAlive(), "getBuffer callback must not attempt a write-lock upgrade under the read fence");
            assertNull(failure.get());
            assertArrayEquals(virtual, CompressionProvider.ZLIB_RAW.decompress(output.get()));
            assertEquals(1, snapshots.get());
        }
    }
    @Test void lowMemoryProviderIdenticalParallelBatchesCompressOnceWithLegacyBytes() throws Exception {
        try (Fixture f = new Fixture()) {
            byte[] input = payload(64 * 1024, 131);
            byte[] expected = new ZlibSingleThreadLowMem().deflateRaw(input, 7);
            class CountingLowMemory extends ZlibSingleThreadLowMem {
                final AtomicInteger calls = new AtomicInteger();
                final Object baselineNativeSafety = new Object();
                @Override public byte[] deflateRaw(byte[] bytes, int level) throws IOException {
                    calls.incrementAndGet();
                    // The old native implementation is unsafe. This fixture guard permits
                    // byte checks while independently counting every actual helper request.
                    synchronized (baselineNativeSafety) { return super.deflateRaw(bytes, level); }
                }
            }
            CountingLowMemory backend = new CountingLowMemory();
            f.provider.set(null, backend);
            ExecutorService pool = flightPool(8);
            CountDownLatch ready = new CountDownLatch(8), start = new CountDownLatch(1);
            try {
                List<Future<byte[]>> outputs = new ArrayList<>();
                for (int i = 0; i < 8; i++) outputs.add(pool.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(3, TimeUnit.SECONDS));
                    return OutboundBatchCompression.compress(CompressionProvider.ZLIB_RAW, new BinaryStream(input), 7, 589);
                }));
                assertTrue(ready.await(3, TimeUnit.SECONDS));
                start.countDown();
                for (Future<byte[]> output : outputs) {
                    byte[] actual = output.get(5, TimeUnit.SECONDS);
                    assertArrayEquals(expected, actual);
                    assertArrayEquals(input, lowMemoryInflateIndependent(actual, true, input.length));
                }
                assertEquals(1, backend.calls.get(), "identical provider1 broadcast recipients require only one native compression");
            } finally {
                start.countDown();
                pool.shutdownNow();
                assertTrue(pool.awaitTermination(3, TimeUnit.SECONDS));
            }
        }
    }

    static byte[] lowMemoryInflateIndependent(byte[] bytes, boolean raw, int size) throws Exception {
        java.util.zip.Inflater inflater = new java.util.zip.Inflater(raw);
        try {
            inflater.setInput(bytes);
            byte[] plain = new byte[size + 1];
            int length = 0;
            while (!inflater.finished()) {
                int count = inflater.inflate(plain, length, plain.length - length);
                assertTrue(count > 0 || inflater.finished(), "independent inflater must make progress");
                length += count;
                assertTrue(length <= size);
            }
            assertEquals(size, length);
            assertEquals(0, inflater.getRemaining());
            return Arrays.copyOf(plain, length);
        } finally { inflater.end(); }
    }

    static byte[] lowMemoryDeflateReference(byte[][] chunks, boolean raw) throws Exception {
        java.util.zip.Deflater deflater = new java.util.zip.Deflater(7, raw);
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        try {
            if (chunks.length == 1) {
                deflater.setInput(chunks[0]);
                deflater.finish();
            } else {
                for (byte[] chunk : chunks) {
                    deflater.setInput(chunk);
                    while (!deflater.needsInput()) {
                        int count = deflater.deflate(buffer);
                        output.write(buffer, 0, count);
                    }
                }
                deflater.finish();
            }
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } finally { deflater.end(); }
    }

    @Test void lowMemoryProviderMixedApisAcrossInstancesKeepExactLevelSevenBytes() throws Exception {
        ZlibSingleThreadLowMem[] backends = {new ZlibSingleThreadLowMem(), new ZlibSingleThreadLowMem()};
        byte[][] plain = new byte[4][], raw = new byte[4][], wrapped = new byte[4][];
        byte[][] rawChunks = new byte[4][], wrappedChunks = new byte[4][];
        byte[][][] chunks = new byte[4][][];
        for (int worker = 0; worker < 4; worker++) {
            plain[worker] = new byte[16 * 1024];
            new java.util.Random(0x706000L + worker).nextBytes(plain[worker]);
            for (int i = 0; i < plain[worker].length; i += 2) plain[worker][i] = (byte) (i + worker);
            chunks[worker] = new byte[][]{Arrays.copyOfRange(plain[worker], 0, 8192),
                    Arrays.copyOfRange(plain[worker], 8192, plain[worker].length)};
            raw[worker] = lowMemoryDeflateReference(new byte[][]{plain[worker]}, true);
            wrapped[worker] = lowMemoryDeflateReference(new byte[][]{plain[worker]}, false);
            rawChunks[worker] = lowMemoryDeflateReference(chunks[worker], true);
            wrappedChunks[worker] = lowMemoryDeflateReference(chunks[worker], false);
        }
        ExecutorService pool = flightPool(4);
        java.util.concurrent.CyclicBarrier step = new java.util.concurrent.CyclicBarrier(4);
        int[] levels = {-1, 0, 1, 3, 9};
        try {
            List<Future<Void>> jobs = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                final int worker = i;
                jobs.add(pool.submit(() -> {
                    for (int iteration = 0; iteration < 12; iteration++) for (int phase = 0; phase < 6; phase++) {
                        step.await(2, TimeUnit.SECONDS);
                        ZlibSingleThreadLowMem backend = backends[(worker + iteration + phase) & 1];
                        int level = levels[(worker + iteration) % levels.length];
                        byte[] output;
                        switch ((worker + phase) % 6) {
                            case 0 -> {
                                output = backend.deflateRaw(plain[worker], level);
                                assertArrayEquals(raw[worker], output);
                                assertArrayEquals(plain[worker], lowMemoryInflateIndependent(output, true, plain[worker].length));
                            }
                            case 1 -> {
                                output = backend.deflateRaw(chunks[worker], level);
                                assertArrayEquals(rawChunks[worker], output);
                                assertArrayEquals(plain[worker], lowMemoryInflateIndependent(output, true, plain[worker].length));
                            }
                            case 2 -> {
                                output = backend.deflate(plain[worker], level);
                                assertArrayEquals(wrapped[worker], output);
                                assertArrayEquals(plain[worker], lowMemoryInflateIndependent(output, false, plain[worker].length));
                            }
                            case 3 -> {
                                output = backend.deflate(chunks[worker], level);
                                assertArrayEquals(wrappedChunks[worker], output);
                                assertArrayEquals(plain[worker], lowMemoryInflateIndependent(output, false, plain[worker].length));
                            }
                            case 4 -> assertArrayEquals(plain[worker], backend.inflateRaw(raw[worker], plain[worker].length + 1));
                            case 5 -> assertArrayEquals(plain[worker], backend.inflate(wrapped[worker], plain[worker].length + 1));
                            default -> throw new AssertionError("unexpected API index");
                        }
                    }
                    return null;
                }));
            }
            for (Future<Void> job : jobs) job.get(5, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    @Test void lowMemoryProviderKeepsStrictInflateLimitsAndLevelSevenForAllLevels() throws Exception {
        byte[] input = payload(4096, 135);
        byte[][] chunks = {Arrays.copyOfRange(input, 0, 2048), Arrays.copyOfRange(input, 2048, input.length)};
        byte[] raw = lowMemoryDeflateReference(new byte[][]{input}, true);
        byte[] wrapped = lowMemoryDeflateReference(new byte[][]{input}, false);
        for (ZlibSingleThreadLowMem backend : new ZlibSingleThreadLowMem[]{new ZlibSingleThreadLowMem(), new ZlibSingleThreadLowMem()}) {
            for (int level : new int[]{-1, 0, 1, 3, 9}) {
                assertArrayEquals(raw, backend.deflateRaw(input, level));
                assertArrayEquals(wrapped, backend.deflate(input, level));
                assertArrayEquals(lowMemoryDeflateReference(chunks, true), backend.deflateRaw(chunks, level));
                assertArrayEquals(lowMemoryDeflateReference(chunks, false), backend.deflate(chunks, level));
            }
            assertArrayEquals(input, backend.inflateRaw(raw, input.length + 1));
            assertArrayEquals(input, backend.inflate(wrapped, input.length + 1));
            assertThrows(IOException.class, () -> backend.inflateRaw(raw, input.length));
            assertThrows(IOException.class, () -> backend.inflate(wrapped, input.length));
        }
    }
    @Test void lowMemoryProviderKeepsGoldenLevelSevenAcrossWholeProtocolWindowAndUnknownProtocols() throws Exception {
        try (Fixture f = new Fixture()) {
            f.provider.set(null, new ZlibSingleThreadLowMem());
            var protocols = new java.util.TreeSet<Integer>();
            for (GameVersion version : GameVersion.values()) {
                int protocol = version.getProtocol();
                if (!version.isNetEase() && protocol >= 589 && protocol <= 2193) protocols.add(protocol);
            }
            assertTrue(protocols.contains(589));
            assertTrue(protocols.contains(2193));
            protocols.add(0);
            protocols.add(Integer.MAX_VALUE);
            for (int size : new int[]{1024, 4096, 64 * 1024, 128 * 1024}) {
                byte[] input = new byte[size];
                new java.util.Random(0x706100L + size).nextBytes(input);
                for (int i = 0; i < size; i += 4) input[i] = (byte) (i >>> 3);
                // Independent Deflater(7, true): provider1 has always ignored configured levels.
                byte[] golden = lowMemoryDeflateReference(new byte[][]{input}, true);
                for (int threshold : new int[]{0, 256, 128 * 1024 + 1}) {
                    MockServer.get().networkCompressionThreshold = threshold;
                    for (int level : new int[]{-1, 0, 1, 3, 7, 9}) {
                        MockServer.get().networkCompressionLevel = level;
                        for (int protocol : protocols) {
                            byte[] actual = OutboundBatchCompression.compress(CompressionProvider.ZLIB_RAW,
                                    new BinaryStream(input), level, protocol);
                            String context = "provider1 protocol=" + protocol + " size=" + size
                                    + " configured=" + level + " threshold=" + threshold;
                            assertArrayEquals(golden, actual, context);
                            assertArrayEquals(input, lowMemoryInflateIndependent(actual, true, size), context);
                        }
                        assertEquals(level, MockServer.get().networkCompressionLevel);
                    }
                    assertEquals(threshold, MockServer.get().networkCompressionThreshold);
                }
            }
        }
    }

    @Test void lowMemoryStableFenceRetainsPublicConcurrentFallbackAndReleasesAfterFailure() throws Exception {
        try (Fixture f = new Fixture()) {
            ZlibSingleThreadLowMem low = new ZlibSingleThreadLowMem();
            f.provider.set(null, low);
            AtomicBoolean concurrentActionRan = new AtomicBoolean();
            assertNull(Zlib.withConcurrentCompression(() -> {
                concurrentActionRan.set(true);
                return "must not run";
            }));
            assertFalse(concurrentActionRan.get(), "public concurrent API must retain provider1 fallback contract");
            assertSame(low, Zlib.withStableCompression(backend -> backend));
            assertThrows(IllegalStateException.class, () -> Zlib.withStableCompression(backend -> {
                assertSame(low, backend);
                throw new IllegalStateException("injected stable-fence callback failure");
            }));
            ExecutorService pool = flightPool(1);
            try {
                pool.submit(() -> { Zlib.setProvider(2); return null; }).get(3, TimeUnit.SECONDS);
                assertTrue(f.provider.get(null) instanceof ZlibThreadLocal);
                assertSame(f.provider.get(null), Zlib.withStableCompression(backend -> backend));
            } finally {
                pool.shutdownNow();
                assertTrue(pool.awaitTermination(3, TimeUnit.SECONDS));
            }
        }
    }

    @Test void lowMemoryStableFenceBlocksProviderSwitchUntilCapturedBackendActionFinishes() throws Exception {
        try (Fixture f = new Fixture()) {
            ZlibSingleThreadLowMem low = new ZlibSingleThreadLowMem();
            f.provider.set(null, low);
            CountDownLatch insideFence = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch switchStarted = new CountDownLatch(1);
            ExecutorService pool = flightPool(2);
            try {
                Future<ZlibProvider> holder = pool.submit(() -> Zlib.withStableCompression(backend -> {
                    assertSame(low, backend);
                    insideFence.countDown();
                    try {
                        assertTrue(release.await(3, TimeUnit.SECONDS), "stable action must be released by fixture");
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(interrupted);
                    }
                    assertSame(backend, Zlib.withStableCompression(current -> current));
                    return backend;
                }));
                assertTrue(insideFence.await(1, TimeUnit.SECONDS));
                Future<?> switcher = pool.submit(() -> {
                    switchStarted.countDown();
                    Zlib.setProvider(2);
                });
                assertTrue(switchStarted.await(1, TimeUnit.SECONDS));
                assertThrows(java.util.concurrent.TimeoutException.class,
                        () -> switcher.get(100, TimeUnit.MILLISECONDS),
                        "provider changes must remain fenced while captured-backend action executes");
                assertSame(low, f.provider.get(null));
                release.countDown();
                assertSame(low, holder.get(3, TimeUnit.SECONDS));
                switcher.get(3, TimeUnit.SECONDS);
                assertTrue(f.provider.get(null) instanceof ZlibThreadLocal);
            } finally {
                release.countDown();
                pool.shutdownNow();
                assertTrue(pool.awaitTermination(3, TimeUnit.SECONDS));
            }
        }
    }

    @Test void lowMemoryTruncatedWrappedInputReleasesNativeStateWithoutRejectingValidEmptyStream() throws Exception {
        String javaExecutable = java.nio.file.Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String origin = ZlibSingleThreadLowMem.class.getProtectionDomain().getCodeSource().getLocation().toString();
        Process child = new ProcessBuilder(javaExecutable, "-Xmx128m", "-cp", System.getProperty("java.class.path"),
                LowMemoryWrappedRecoveryProbe.class.getName(), origin).redirectErrorStream(true).start();
        try {
            assertTrue(child.waitFor(5, TimeUnit.SECONDS), "truncated input must not hold the global native mutex forever");
            String output = new String(child.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(0, child.exitValue(), output);
            assertTrue(output.contains("P06_LOW_MEMORY_RECOVERY_OK"), output);
        } finally {
            if (child.isAlive()) child.destroyForcibly();
            assertTrue(child.waitFor(5, TimeUnit.SECONDS), "native regression child must be reaped");
        }
    }

    /** A separate, bounded JVM ensures a broken inflate loop cannot poison other tests. */
    public static final class LowMemoryWrappedRecoveryProbe {
        public static void main(String[] args) throws Exception {
            assertEquals(args[0], ZlibSingleThreadLowMem.class.getProtectionDomain().getCodeSource().getLocation().toString());
            ZlibSingleThreadLowMem first = new ZlibSingleThreadLowMem();
            byte[] empty = first.deflate(new byte[0], 7);
            assertArrayEquals(new byte[0], first.inflate(empty, 1));
            assertThrows(IOException.class, () -> first.inflate(new byte[]{0x78}, 1024));
            byte[] input = payload(16 * 1024, 149);
            ZlibSingleThreadLowMem second = new ZlibSingleThreadLowMem();
            byte[] raw = second.deflateRaw(input, 7);
            assertArrayEquals(lowMemoryDeflateReference(new byte[][]{input}, true), raw);
            assertArrayEquals(input, lowMemoryInflateIndependent(raw, true, input.length));
            byte[] wrapped = second.deflate(input, 7);
            assertArrayEquals(lowMemoryDeflateReference(new byte[][]{input}, false), wrapped);
            assertArrayEquals(input, first.inflate(wrapped, input.length + 1));
            System.out.println("P06_LOW_MEMORY_RECOVERY_OK");
        }
    }
    @Test void snappyKeepsNativeBytesAcrossWindowUnknownKeysBothTransportsAndPrefixes() throws Exception {
        try (Fixture f = new Fixture()) {
            byte[] input = payload(4096, 201);
            byte[] expected = CompressionProvider.SNAPPY.compress(new BinaryStream(input), 7);
            var protocols = new java.util.TreeSet<Integer>();
            for (GameVersion version : GameVersion.values()) {
                int protocol = version.getProtocol();
                if (!version.isNetEase() && protocol >= 589 && protocol <= 2193) protocols.add(protocol);
            }
            assertTrue(protocols.contains(589));
            assertTrue(protocols.contains(2193));
            for (int protocol : protocols) {
                // First RakNet/NetherNet pair delivers the same key from a cold cache.
                for (boolean negotiated : new boolean[]{false, true}) {
                    for (Class<?> transport : new Class<?>[]{RakNetPlayerSession.class, NetherNetPlayerSession.class}) {
                        byte[] actual = send(transport, input, protocol, CompressionProvider.SNAPPY,
                                negotiated, null, 0);
                        String context = "SNAPPY " + transport.getSimpleName() + " protocol=" + protocol
                                + " negotiated=" + negotiated;
                        // send also checks RakNet 0xfe and negotiated algorithm prefixes.
                        assertArrayEquals(expected, actual, context);
                        assertArrayEquals(input, CompressionProvider.SNAPPY.decompress(actual), context);
                    }
                }
            }
            for (int protocol : new int[]{0, Integer.MAX_VALUE}) {
                byte[] actual = OutboundBatchCompression.compress(CompressionProvider.SNAPPY,
                        new BinaryStream(input), 7, protocol);
                assertArrayEquals(expected, actual, "unknown protocol key=" + protocol);
                assertArrayEquals(input, CompressionProvider.SNAPPY.decompress(actual));
            }
            assertEquals(0, f.compressor.calls.get(), "Snappy must never use the Zlib provider");
        }
    }

    @Test void snappyTrueSampleFingerprintCollisionStillRequiresFullPayloadEquality() throws Exception {
        try (Fixture f = new Fixture()) {
            byte[] first = payload(4096, 203);
            byte[] second = first.clone();
            second[200]++; // Unsampled position: identical full 32-bit fingerprint, not merely slot index.
            Method hash = OutboundBatchCompression.class.getDeclaredMethod("slotHash", byte[].class, int.class);
            hash.setAccessible(true);
            assertEquals(hash.invoke(null, first, first.length), hash.invoke(null, second, second.length));
            assertFalse(Arrays.equals(first, second));
            byte[] firstOutput = OutboundBatchCompression.compress(CompressionProvider.SNAPPY,
                    new BinaryStream(first), 7, 589);
            byte[] secondOutput = OutboundBatchCompression.compress(CompressionProvider.SNAPPY,
                    new BinaryStream(second), 7, 589);
            assertFalse(Arrays.equals(firstOutput, secondOutput));
            assertArrayEquals(CompressionProvider.SNAPPY.compress(new BinaryStream(first), 7), firstOutput);
            assertArrayEquals(CompressionProvider.SNAPPY.compress(new BinaryStream(second), 7), secondOutput);
            assertArrayEquals(first, CompressionProvider.SNAPPY.decompress(firstOutput));
            assertArrayEquals(second, CompressionProvider.SNAPPY.decompress(secondOutput));
            byte[] repeatedFirst = OutboundBatchCompression.compress(CompressionProvider.SNAPPY,
                    new BinaryStream(first), 7, 589);
            assertArrayEquals(firstOutput, repeatedFirst);
            assertArrayEquals(first, CompressionProvider.SNAPPY.decompress(repeatedFirst));
        }
    }

    @Test void snappyOwnsBorrowedAndVirtualSnapshotsBeforeCallerMutation() throws Exception {
        try (Fixture f = new Fixture()) {
            byte[] borrowed = payload(4096, 205);
            byte[] original = borrowed.clone();
            BinaryStream plain = new BinaryStream(borrowed);
            byte[] originalOutput = OutboundBatchCompression.compress(CompressionProvider.SNAPPY, plain, 7, 589);
            assertArrayEquals(original, borrowed, "native compression must not mutate caller-owned input");
            borrowed[200]++;
            byte[] changedOutput = OutboundBatchCompression.compress(CompressionProvider.SNAPPY, plain, 7, 589);
            assertArrayEquals(original, CompressionProvider.SNAPPY.decompress(originalOutput));
            assertArrayEquals(borrowed, CompressionProvider.SNAPPY.decompress(changedOutput));
            assertArrayEquals(CompressionProvider.SNAPPY.compress(new BinaryStream(borrowed), 7), changedOutput);

            byte[] virtual = payload(4096, 207);
            byte[] beforeMutation = virtual.clone();
            AtomicInteger snapshots = new AtomicInteger();
            BinaryStream custom = new BinaryStream(payload(64, 209)) {
                @Override public byte[] getBuffer() { snapshots.incrementAndGet(); return virtual; }
                @Override public byte[] getBufferUnsafe() { throw new AssertionError("virtual raw view must not be borrowed"); }
            };
            byte[] oldVirtualOutput = OutboundBatchCompression.compress(CompressionProvider.SNAPPY, custom, 7, 589);
            assertArrayEquals(beforeMutation, virtual);
            virtual[200]++;
            byte[] newVirtualOutput = OutboundBatchCompression.compress(CompressionProvider.SNAPPY, custom, 7, 589);
            assertEquals(2, snapshots.get(), "one virtual snapshot per invocation");
            assertArrayEquals(beforeMutation, CompressionProvider.SNAPPY.decompress(oldVirtualOutput));
            assertArrayEquals(virtual, CompressionProvider.SNAPPY.decompress(newVirtualOutput));
            assertArrayEquals(CompressionProvider.SNAPPY.compress(new BinaryStream(beforeMutation), 7), oldVirtualOutput);
            assertArrayEquals(CompressionProvider.SNAPPY.compress(new BinaryStream(virtual), 7), newVirtualOutput);
            assertEquals(0, f.compressor.calls.get());
        }
    }
}
