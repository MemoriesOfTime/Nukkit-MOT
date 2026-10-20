package cn.nukkit.network.session;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Random;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

class RakNetPlayerSessionChecksumTest {
    private static final long[] COUNTERS = {0, 1, 127, 128, 255, 256, 65535, 65536,
            0x0102030405060708L, Long.MAX_VALUE, Long.MIN_VALUE, -1};

    @Test
    void keepsKnownLittleEndianVectorAndReceivedTrailer() throws Exception {
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) key[i] = (byte) i;
        RakNetPlayerSession session = session(key);
        Field localField = RakNetPlayerSession.class.getDeclaredField("CHECKSUM_LOCAL");
        localField.setAccessible(true);
        byte[] receivedTrailer = ((ThreadLocal<byte[]>) localField.get(null)).get();
        Arrays.fill(receivedTrailer, (byte) 0xa5);
        byte[] expectedTrailer = receivedTrailer.clone();
        ByteBuf payload = Unpooled.wrappedBuffer(new byte[]{0, 127, (byte) 128, (byte) 255, 1, 2, 3});
        try {
            byte[] actual = calculate(session, 0x0102030405060708L, payload);
            assertEquals("4fd7df1ec998997f", HexFormat.of().formatHex(actual));
            assertArrayEquals(expectedTrailer, receivedTrailer,
                    "incoming verification must keep its received trailer after calculating the expected one");
            actual[0] ^= (byte) 0xff;
            assertEquals("4fd7df1ec998997f", HexFormat.of().formatHex(calculate(session, 0x0102030405060708L, payload)));
        } finally {
            payload.release();
        }
    }

    @Test
    void matchesIndependentDigestForCountersAndBufferViewsOnIndependentThreads() throws Exception {
        var threads = Executors.newFixedThreadPool(3);
        try {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int task = 0; task < 6; task++) {
                final int seed = task;
                futures.add(threads.submit(() -> {
                    try {
                        Random random = new Random(seed);
                        byte[] key = new byte[32];
                        random.nextBytes(key);
                        RakNetPlayerSession session = session(key);
                        for (int length : new int[]{0, 1, 7, 8, 31, 256, 1025}) {
                            byte[] bytes = new byte[length];
                            random.nextBytes(bytes);
                            for (boolean direct : new boolean[]{false, true}) {
                                ByteBuf backing = direct ? Unpooled.directBuffer(length + 13) : Unpooled.buffer(length + 13);
                                try {
                                    backing.writeZero(5).writeBytes(bytes).writeZero(8);
                                    ByteBuf payload = backing.slice(2, length + 3).readerIndex(3);
                                    int reader = payload.readerIndex();
                                    int writer = payload.writerIndex();
                                    for (long counter : COUNTERS) {
                                        MessageDigest digest = MessageDigest.getInstance("SHA-256");
                                        digest.update(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(counter).array());
                                        digest.update(bytes);
                                        digest.update(key);
                                        assertArrayEquals(Arrays.copyOf(digest.digest(), 8), calculate(session, counter, payload));
                                        assertEquals(reader, payload.readerIndex());
                                        assertEquals(writer, payload.writerIndex());
                                        assertEquals(1, backing.refCnt());
                                    }
                                } finally {
                                    backing.release();
                                }
                            }
                        }
                    } catch (Exception e) {
                        throw new AssertionError(e);
                    }
                }));
            }
            for (var future : futures) future.get();
        } finally {
            threads.shutdownNow();
        }
    }

    private static RakNetPlayerSession session(byte[] key) throws Exception {
        RakNetPlayerSession session = mock(RakNetPlayerSession.class, CALLS_REAL_METHODS);
        Field field = RakNetPlayerSession.class.getDeclaredField("encryptionKey");
        field.setAccessible(true);
        field.set(session, new SecretKeySpec(key, "AES"));
        return session;
    }

    private static byte[] calculate(RakNetPlayerSession session, long counter, ByteBuf payload) throws Exception {
        Method method = RakNetPlayerSession.class.getDeclaredMethod("calculateChecksum", long.class, ByteBuf.class);
        method.setAccessible(true);
        return (byte[]) method.invoke(session, counter, payload);
    }
}
