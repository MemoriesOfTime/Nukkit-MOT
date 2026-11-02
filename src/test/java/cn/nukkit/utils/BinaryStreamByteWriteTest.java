package cn.nukkit.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Public writer contracts; deliberately independent of dispatch-cache implementation. */
class BinaryStreamByteWriteTest {
    private static final int RETAINED_LIMIT = 1024 * 1024;

    @BeforeEach
    @AfterEach
    void clearThreadCache() {
        ThreadCache.binaryStream.remove();
    }

    @Test
    void byteOverloadWritesAll256BitPatternsWithoutMovingReadOffset() {
        BinaryStream stream = new BinaryStream(0);
        byte[] expected = new byte[256];
        for (int value = 0; value < 256; value++) {
            expected[value] = (byte) value;
            stream.putByte((byte) value);
            assertEquals(value + 1, stream.getCount());
            assertEquals(0, stream.getOffset());
        }
        assertArrayEquals(expected, stream.getBuffer());
    }

    @Test
    void intOverloadTruncatesEveryLowByteAndSignedBoundary() {
        BinaryStream stream = new BinaryStream(0);
        int[] highParts = {Integer.MIN_VALUE, -65536, -256, 0, 256, 65536, 0x7fffff00};
        byte[] expected = new byte[highParts.length * 256];
        int at = 0;
        for (int highPart : highParts) {
            for (int lowByte = 0; lowByte < 256; lowByte++) {
                stream.putByte(highPart | lowByte);
                expected[at++] = (byte) lowByte;
            }
        }
        assertEquals(expected.length, stream.getCount());
        assertArrayEquals(expected, stream.getBuffer());
        assertEquals(0, stream.getOffset());
    }

    @Test
    void everyConstructorCanAppendAndGrowWithoutLosingExistingBytes() {
        List<BinaryStream> streams = List.of(new BinaryStream(), new BinaryStream(0),
                new BinaryStream(1), new BinaryStream(new byte[]{0x11, 0x22}),
                new BinaryStream(new byte[]{0x33, 0x44}, 1), new PlainInheritedStream());
        for (BinaryStream stream : streams) {
            byte[] prefix = stream.getBuffer();
            int offset = stream.getOffset();
            byte[] expected = Arrays.copyOf(prefix, prefix.length + 257);
            for (int i = 0; i < 257; i++) {
                expected[prefix.length + i] = (byte) (i * 17);
                stream.putByte((byte) (i * 17));
            }
            assertArrayEquals(expected, stream.getBuffer());
            assertEquals(expected.length, stream.getCount());
            assertTrue(stream.getBufferUnsafe().length >= expected.length);
            assertEquals(offset, stream.getOffset());
        }
    }

    @Test
    void invalidNegativeCountDoesNotAdvanceCountOrChangeBuffer() {
        for (int invalidCount : new int[]{-1, -2, -31}) {
            for (boolean intOverload : new boolean[]{false, true}) {
                BinaryStream stream = new BinaryStream(new byte[]{0x11, 0x22, 0x33}, 1);
                stream.setCount(invalidCount);
                byte[] backing = stream.getBufferUnsafe();
                byte[] before = backing.clone();
                assertThrows(IndexOutOfBoundsException.class, () -> {
                    if (intOverload) stream.putByte(0x1ff);
                    else stream.putByte((byte) 0x7f);
                });
                assertEquals(invalidCount, stream.getCount());
                assertEquals(1, stream.getOffset());
                assertSame(backing, stream.getBufferUnsafe());
                assertArrayEquals(before, backing);
            }
        }
    }

    @Test
    void nullBufferFailurePreservesNullCountAndOffset() {
        BinaryStream stream = new BinaryStream();
        stream.setBuffer(null, 7);
        assertThrows(NullPointerException.class, () -> stream.putByte((byte) 42));
        assertNull(stream.getBufferUnsafe());
        assertEquals(-1, stream.getCount());
        assertEquals(7, stream.getOffset());
    }

    @Test
    void unsignedVarLongUsesIndependentLiteralWireOracleAtEverySevenBitBoundary() {
        Object[][] vectors = {
                {0L, "00"}, {1L, "01"}, {127L, "7f"}, {128L, "8001"}, {255L, "ff01"},
                {16383L, "ff7f"}, {16384L, "808001"},
                {2097151L, "ffff7f"}, {2097152L, "80808001"},
                {268435455L, "ffffff7f"}, {268435456L, "8080808001"},
                {34359738367L, "ffffffff7f"}, {34359738368L, "808080808001"},
                {4398046511103L, "ffffffffff7f"}, {4398046511104L, "80808080808001"},
                {562949953421311L, "ffffffffffff7f"}, {562949953421312L, "8080808080808001"},
                {72057594037927935L, "ffffffffffffff7f"}, {72057594037927936L, "808080808080808001"},
                {Long.MAX_VALUE, "ffffffffffffffff7f"},
                {Long.MIN_VALUE, "80808080808080808001"},
                {-2L, "feffffffffffffffff01"}, {-1L, "ffffffffffffffffff01"}
        };
        for (Object[] vector : vectors) {
            long value = (Long) vector[0];
            byte[] wire = HexFormat.of().parseHex((String) vector[1]);
            for (BinaryStream stream : List.of(new BinaryStream(0), new RecordingPutStream(),
                    new InheritedPutStream())) {
                stream.setBuffer(new byte[]{0x55}, 1);
                stream.putUnsignedVarLong(value);
                byte[] expected = new byte[wire.length + 1];
                expected[0] = 0x55;
                System.arraycopy(wire, 0, expected, 1, wire.length);
                assertArrayEquals(expected, stream.getBuffer(), "value=" + Long.toUnsignedString(value));
                assertEquals(expected.length, stream.getCount());
                assertEquals(1, stream.getOffset());
                if (stream instanceof RecordingPutStream recording) {
                    assertEquals(wire.length, recording.calls.size());
                    for (int i = 0; i < wire.length; i++) {
                        assertArrayEquals(new byte[]{wire[i]}, recording.calls.get(i));
                    }
                }
                assertEquals(value, stream.getUnsignedVarLong());
                assertEquals(expected.length, stream.getOffset());
            }
        }
    }

    @Test
    void customPutMayTransformEachSingleByteWrite() {
        TransformingPutStream stream = new TransformingPutStream();
        stream.putByte((byte) 0x12);
        stream.putByte(0x134);
        assertEquals(2, stream.calls);
        assertArrayEquals(new byte[]{0x6a, (byte) 0xed, 0x6a, (byte) 0xcb}, stream.getBuffer());
        assertEquals(4, stream.getCount());
    }

    @Test
    void inheritedPutOverrideReceivesBothByteOverloads() {
        for (RecordingPutStream stream : List.of(new RecordingPutStream(), new InheritedPutStream())) {
            stream.putByte((byte) 0x91);
            stream.putByte(0x1ff);
            assertEquals(2, stream.calls.size());
            assertArrayEquals(new byte[]{(byte) 0x91}, stream.calls.get(0));
            assertArrayEquals(new byte[]{(byte) 0xff}, stream.calls.get(1));
            assertArrayEquals(new byte[]{(byte) 0x91, (byte) 0xff}, stream.getBuffer());
        }
    }

    @Test
    void putOverrideReceivesFreshArrayPerCallIncludingRepeatedValues() {
        RecordingPutStream stream = new InheritedPutStream();
        stream.putByte((byte) 0x55);
        stream.putByte((byte) 0x55);
        stream.putByte(0x155);
        assertEquals(3, stream.calls.size());
        for (int i = 0; i < stream.calls.size(); i++) {
            assertArrayEquals(new byte[]{0x55}, stream.calls.get(i));
            for (int j = i + 1; j < stream.calls.size(); j++) {
                assertNotSame(stream.calls.get(i), stream.calls.get(j));
            }
        }
        stream.calls.get(0)[0] = 0;
        assertArrayEquals(new byte[]{0x55}, stream.calls.get(1));
        assertArrayEquals(new byte[]{0x55}, stream.calls.get(2));
        assertArrayEquals(new byte[]{0x55, 0x55, 0x55}, stream.getBuffer());
    }

    @Test
    void noOpPutOverrideDoesNotWriteOrAdvanceAnything() {
        NoOpPutStream stream = new NoOpPutStream();
        stream.setBuffer(new byte[]{0x11, 0x22}, 1);
        byte[] backing = stream.getBufferUnsafe();
        stream.putByte((byte) 0x33);
        stream.putByte(0x144);
        assertEquals(2, stream.calls.size());
        assertArrayEquals(new byte[]{0x33}, stream.calls.get(0));
        assertArrayEquals(new byte[]{0x44}, stream.calls.get(1));
        assertNotSame(stream.calls.get(0), stream.calls.get(1));
        assertEquals(2, stream.getCount());
        assertEquals(1, stream.getOffset());
        assertSame(backing, stream.getBufferUnsafe());
        assertArrayEquals(new byte[]{0x11, 0x22}, stream.getBuffer());
    }

    @Test
    void throwingPutOverridePreservesExceptionIdentityStateAndFreshArguments() {
        ThrowingPutStream stream = new ThrowingPutStream();
        stream.setBuffer(new byte[]{0x11, 0x22}, 1);
        byte[] backing = stream.getBufferUnsafe();
        assertSame(stream.failure, assertThrows(IllegalStateException.class,
                () -> stream.putByte((byte) 0x33)));
        assertSame(stream.failure, assertThrows(IllegalStateException.class,
                () -> stream.putByte(0x144)));
        assertEquals(2, stream.calls.size());
        assertArrayEquals(new byte[]{0x33}, stream.calls.get(0));
        assertArrayEquals(new byte[]{0x44}, stream.calls.get(1));
        assertNotSame(stream.calls.get(0), stream.calls.get(1));
        assertSame(backing, stream.getBufferUnsafe());
        assertArrayEquals(new byte[]{0x11, 0x22}, stream.getBuffer());
        assertEquals(2, stream.getCount());
        assertEquals(1, stream.getOffset());
    }

    @Test
    void intOverloadStillDispatchesToOverriddenByteOverloadAfterTruncation() {
        ByteOverrideStream stream = new ByteOverrideStream();
        BinaryStream api = stream;
        api.putByte(0x1ff);
        api.putByte(Integer.MIN_VALUE);
        assertEquals(2, stream.calls.size());
        assertEquals((byte) 0xff, stream.calls.get(0).byteValue());
        assertEquals((byte) 0, stream.calls.get(1).byteValue());
        assertArrayEquals(new byte[]{(byte) 0xaa, 0x55}, stream.getBuffer());
    }

    @Test
    void resetAndReuseKeepCapacityButStartANewLogicalStream() {
        for (BinaryStream stream : List.of(new BinaryStream(1), new InheritedPutStream())) {
            for (int i = 0; i < 257; i++) stream.putByte(i);
            byte[] backing = stream.getBufferUnsafe();
            stream.setOffset(17);
            assertSame(stream, stream.reset());
            assertEquals(0, stream.getCount());
            assertEquals(0, stream.getOffset());
            assertSame(backing, stream.getBufferUnsafe());
            assertArrayEquals(new byte[0], stream.getBuffer());
            stream.putByte(0x180);
            assertArrayEquals(new byte[]{(byte) 0x80}, stream.getBuffer());
            stream.setOffset(1);
            stream.reuse();
            assertEquals(0, stream.getCount());
            assertEquals(0, stream.getOffset());
            assertSame(backing, stream.getBufferUnsafe());
            stream.putByte((byte) 0x7f);
            assertArrayEquals(new byte[]{0x7f}, stream.getBuffer());
        }
    }

    @Test
    void cachedByteCrossingOneMiBLimitDetachesBeforeSnapshotOrReset() {
        BinaryStream stream = cachedAtLimit();
        stream.setOffset(17);
        stream.putByte((byte) 0x7e);
        assertEquals(RETAINED_LIMIT + 1, stream.getCount());
        assertEquals(17, stream.getOffset());
        assertEquals((byte) 0x7e, stream.getBufferUnsafe()[RETAINED_LIMIT]);
        assertTrue(stream.getBufferUnsafe().length > RETAINED_LIMIT);
        BinaryStream replacement = ThreadCache.binaryStream.get();
        assertNotSame(stream, replacement);
        assertEquals(0, replacement.getCount());
        assertTrue(replacement.getBufferUnsafe().length <= RETAINED_LIMIT);
        assertFilledPrefix(stream, RETAINED_LIMIT, (byte) 0x5a);
    }

    @Test
    void encoderFailureAfterOversizedByteWriteDoesNotRetainBuffer() {
        BinaryStream stream = cachedAtLimit();
        IllegalStateException failure = new IllegalStateException("encoder failed after write");
        assertSame(failure, assertThrows(IllegalStateException.class, () -> {
            stream.putByte(0x1fe);
            throw failure;
        }));
        assertEquals(RETAINED_LIMIT + 1, stream.getCount());
        assertEquals((byte) 0xfe, stream.getBufferUnsafe()[RETAINED_LIMIT]);
        assertNotSame(stream, ThreadCache.binaryStream.get());
    }

    @Test
    void invalidWriteOnOversizedDetachedStreamPreservesReplacementAndState() {
        BinaryStream stale = cachedAtLimit();
        stale.putByte((byte) 1);
        BinaryStream replacement = ThreadCache.binaryStream.get();
        assertNotSame(stale, replacement);
        replacement.putByte((byte) 0x66);
        stale.setCount(-1);
        stale.setOffset(19);
        byte[] backing = stale.getBufferUnsafe();
        byte[] before = backing.clone();
        assertThrows(IndexOutOfBoundsException.class, () -> stale.putByte((byte) 2));
        assertEquals(-1, stale.getCount());
        assertEquals(19, stale.getOffset());
        assertSame(backing, stale.getBufferUnsafe());
        assertArrayEquals(before, backing);
        assertSame(replacement, ThreadCache.binaryStream.get());
        assertArrayEquals(new byte[]{0x66}, replacement.getBuffer());
    }

    @Test
    void staleOversizedInstanceCannotEvictReplacementAfterMoreWritesOrReuse() {
        BinaryStream stale = cachedAtLimit();
        stale.putByte((byte) 1);
        BinaryStream replacement = ThreadCache.binaryStream.get();
        assertNotSame(stale, replacement);
        replacement.putByte((byte) 0x66);
        stale.putByte(0x102);
        stale.put(new byte[]{3});
        stale.reset();
        stale.putByte((byte) 4);
        stale.reuse();
        stale.putByte(5);
        assertSame(replacement, ThreadCache.binaryStream.get());
        assertArrayEquals(new byte[]{0x66}, replacement.getBuffer());
        assertArrayEquals(new byte[]{5}, stale.getBuffer());
        assertTrue(stale.getBufferUnsafe().length > RETAINED_LIMIT);
    }

    @Test
    void smallCachedStreamSurvivesResetReuseAndFailedWrite() {
        BinaryStream stream = ThreadCache.binaryStream.get();
        stream.putByte((byte) 1);
        stream.setCount(-1);
        assertThrows(IndexOutOfBoundsException.class, () -> stream.putByte(2));
        assertEquals(-1, stream.getCount());
        assertSame(stream, ThreadCache.binaryStream.get());
        stream.reset();
        stream.putByte(3);
        stream.reuse();
        stream.putByte((byte) 4);
        assertSame(stream, ThreadCache.binaryStream.get());
        assertArrayEquals(new byte[]{4}, stream.getBuffer());
    }

    private static BinaryStream cachedAtLimit() {
        BinaryStream stream = ThreadCache.binaryStream.get();
        byte[] payload = new byte[RETAINED_LIMIT];
        Arrays.fill(payload, (byte) 0x5a);
        stream.put(payload);
        assertEquals(RETAINED_LIMIT, stream.getCount());
        assertSame(stream, ThreadCache.binaryStream.get());
        return stream;
    }

    private static void assertFilledPrefix(BinaryStream stream, int length, byte expected) {
        byte[] prefix = Arrays.copyOf(stream.getBufferUnsafe(), length);
        byte[] expectedPrefix = new byte[length];
        Arrays.fill(expectedPrefix, expected);
        assertArrayEquals(expectedPrefix, prefix);
    }

    private static final class PlainInheritedStream extends BinaryStream {}

    private static class RecordingPutStream extends BinaryStream {
        final List<byte[]> calls = new ArrayList<>();

        @Override
        public void put(byte[] bytes) {
            calls.add(bytes);
            super.put(bytes);
        }
    }

    private static final class InheritedPutStream extends RecordingPutStream {}

    private static final class TransformingPutStream extends BinaryStream {
        int calls;

        @Override
        public void put(byte[] bytes) {
            calls++;
            assertEquals(1, bytes.length);
            super.put(new byte[]{0x6a, (byte) (bytes[0] ^ 0xff)});
        }
    }

    private static final class NoOpPutStream extends BinaryStream {
        final List<byte[]> calls = new ArrayList<>();

        @Override
        public void put(byte[] bytes) {
            calls.add(bytes);
        }
    }

    private static final class ThrowingPutStream extends BinaryStream {
        final List<byte[]> calls = new ArrayList<>();
        final IllegalStateException failure = new IllegalStateException("put rejected");

        @Override
        public void put(byte[] bytes) {
            calls.add(bytes);
            throw failure;
        }
    }

    private static final class ByteOverrideStream extends BinaryStream {
        final List<Byte> calls = new ArrayList<>();

        @Override
        public void putByte(byte value) {
            calls.add(value);
            super.putByte((byte) (value ^ 0x55));
        }
    }
}
