package cn.nukkit.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class BinaryStreamFixedWidthReadTest {
    enum Kind {
        LONG(8), INT(4), LONG_LE(8), SHORT(2), SHORT_LE(2), FLOAT(4), TRIAD(3), TRIAD_LE(3);
        final int width;
        Kind(int width) { this.width = width; }
        Object read(BinaryStream stream) {
            return switch (this) {
                case LONG -> stream.getLong();
                case INT -> stream.getInt();
                case LONG_LE -> stream.getLLong();
                case SHORT -> stream.getShort();
                case SHORT_LE -> stream.getLShort();
                case FLOAT -> Float.floatToRawIntBits(stream.getFloat());
                case TRIAD -> stream.getTriad();
                case TRIAD_LE -> stream.getLTriad();
            };
        }
        Object original(BinaryStream stream) {
            byte[] bytes = stream.get(width);
            return switch (this) {
                case LONG -> Binary.readLong(bytes);
                case INT -> Binary.readInt(bytes);
                case LONG_LE -> Binary.readLLong(bytes);
                case SHORT -> Binary.readShort(bytes);
                case SHORT_LE -> Binary.readLShort(bytes);
                case FLOAT -> Float.floatToRawIntBits(Binary.readFloat(bytes, -1));
                case TRIAD -> Binary.readTriad(bytes);
                case TRIAD_LE -> Binary.readLTriad(bytes);
            };
        }
    }

    @ParameterizedTest @EnumSource(Kind.class)
    void matchesRandomBytesAtEveryValidOffset(Kind kind) {
        Random random = new Random(0x1a43bd99L);
        for (int iteration = 0; iteration < 128; iteration++) {
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            for (int offset = 0; offset <= bytes.length - kind.width; offset++) {
                compare(kind, bytes, bytes.length, offset);
            }
        }
    }

    @ParameterizedTest @EnumSource(Kind.class)
    void preservesMalformedCursorCountAndBufferStates(Kind kind) {
        for (int length = -1; length <= 12; length++) {
            byte[] bytes = length < 0 ? null : new byte[length];
            if (bytes != null) new Random(length).nextBytes(bytes);
            for (int count : new int[]{-2, -1, 0, 1, 2, 3, 4, 7, 8, 9, 16, Integer.MAX_VALUE}) {
                for (int offset : new int[]{Integer.MIN_VALUE, -3, -1, 0, 1, 2, 3, 8, 16, Integer.MAX_VALUE}) {
                    compare(kind, bytes, count, offset);
                }
            }
        }
    }

    @ParameterizedTest @EnumSource(Kind.class)
    void preservesGetOverrideAndItsFailureIdentity(Kind kind) {
        byte[] returned = new byte[8];
        new Random(99).nextBytes(returned);
        int[] calls = {0};
        BinaryStream stream = new BinaryStream(new byte[32], 1) {
            @Override public byte[] get(int width) {
                assertEquals(kind.width, width);
                calls[0]++;
                return returned;
            }
        };
        Object expected = kind.original(new BinaryStream(returned));
        assertEquals(expected, kind.read(stream));
        assertEquals(1, calls[0]);
        assertEquals(1, stream.getOffset());
        RuntimeException failure = new IllegalStateException("fixture");
        BinaryStream failing = new BinaryStream(new byte[32]) {
            @Override public byte[] get(int width) { throw failure; }
        };
        assertSame(failure, assertThrows(IllegalStateException.class, () -> kind.read(failing)));
    }

    @ParameterizedTest @EnumSource(Kind.class)
    void inheritedReadersKeepSameValuesAndCursor(Kind kind) {
        byte[] bytes = new byte[16];
        new Random(129).nextBytes(bytes);
        class Inherited extends BinaryStream {
            Inherited(byte[] input) { super(input, 2); }
        }
        BinaryStream expected = new BinaryStream(bytes, 2), actual = new Inherited(bytes);
        assertEquals(kind.original(expected), kind.read(actual));
        assertEquals(expected.getOffset(), actual.getOffset());
    }

    @Test
    void floatPayloadBitsAccuracyAndVirtualDispatchStayUnchanged() {
        for (int bits : new int[]{0, 0x80000000, 0x7f800000, 0xff800000, 0x7fc12345,
                0xffc54321, 1, -1, 0x3f9e0652}) {
            byte[] bytes = Binary.writeInt(bits);
            for (int accuracy : new int[]{-2, -1, 0, 1, 3, 1000, Integer.MAX_VALUE}) {
                BinaryStream stream = new BinaryStream(bytes) {
                    @Override public int getInt() { throw new AssertionError("unexpected getInt interception"); }
                };
                assertEquals(Float.floatToRawIntBits(Binary.readFloat(bytes, accuracy)),
                        Float.floatToRawIntBits(stream.getFloat(accuracy)));
            }
        }
        BinaryStream intercepted = new BinaryStream(new byte[8]) {
            @Override public float getFloat(int accuracy) { assertEquals(-1, accuracy); return 1.25f; }
        };
        assertEquals(1.25f, intercepted.getFloat());
    }

    private static void compare(Kind kind, byte[] bytes, int count, int offset) {
        BinaryStream expected = state(bytes, count, offset), actual = state(bytes, count, offset);
        Object expectedValue = null, actualValue = null;
        Throwable expectedFailure = null, actualFailure = null;
        try { expectedValue = kind.original(expected); } catch (RuntimeException failure) { expectedFailure = failure; }
        try { actualValue = kind.read(actual); } catch (RuntimeException failure) { actualFailure = failure; }
        if (expectedFailure == null) {
            assertNull(actualFailure);
            assertEquals(expectedValue, actualValue);
        } else {
            assertNotNull(actualFailure);
            assertEquals(expectedFailure.getClass(), actualFailure.getClass());
            // HotSpot 可将同一 PC 上反复抛出的隐式异常（AIOOBE/NPE）换成无消息无栈的预分配异常，
            // 旧/新两条内联路径的异常 PC 各自计数，可能一侧已省略一侧仍保留，消息文本不可比。
            // 构建 JVM 已传 -XX:-OmitStackTraceInFastThrow 根除此现象，此处仅兜底不经构建的入口（IDE/main）。
            // HotSpot may swap repeatedly thrown implicit exceptions (AIOOBE/NPE) at one PC for a
            // preallocated one without message or stack; the two inlined sides can diverge. Build JVMs
            // pass -XX:-OmitStackTraceInFastThrow to prevent it; this guard only covers entry points
            // without the flag (IDE runs, the main method below).
            if (!omittedByFastThrow(expectedFailure) && !omittedByFastThrow(actualFailure)) {
                assertEquals(expectedFailure.getMessage(), actualFailure.getMessage());
            }
        }
        assertEquals(expected.getOffset(), actual.getOffset());
        assertEquals(expected.getCount(), actual.getCount());
        assertArrayEquals(bytes, actual.getBufferUnsafe());
    }

    private static boolean omittedByFastThrow(Throwable failure) {
        // 同一 PC 反复抛出的隐式异常（含 copyOfRange 的 null 数组 NPE）会被 HotSpot
        // 以无消息、无栈的预分配异常替代，此时消息文本不参与一致性比较。
        // Implicit exceptions thrown repeatedly at one PC (including the null-array NPE
        // from copyOfRange) get replaced by a preallocated message-less, stack-less
        // exception whose text must not take part in the comparison.
        return (failure.getClass() == ArrayIndexOutOfBoundsException.class
                || failure.getClass() == NullPointerException.class)
                && failure.getMessage() == null && failure.getStackTrace().length == 0;
    }

    private static BinaryStream state(byte[] bytes, int count, int offset) {
        BinaryStream stream = new BinaryStream();
        stream.setBuffer(bytes == null ? null : bytes.clone());
        stream.setCount(count);
        stream.setOffset(offset);
        return stream;
    }

    /** Run against immutable baseline classes before rebuilding the modified source. */
    public static void main(String[] args) {
        BinaryStreamFixedWidthReadTest test = new BinaryStreamFixedWidthReadTest();
        for (Kind kind : Kind.values()) {
            test.matchesRandomBytesAtEveryValidOffset(kind);
            test.preservesMalformedCursorCountAndBufferStates(kind);
            test.preservesGetOverrideAndItsFailureIdentity(kind);
            test.inheritedReadersKeepSameValuesAndCursor(kind);
        }
        test.floatPayloadBitsAccuracyAndVirtualDispatchStayUnchanged();
        System.out.println("BinaryStreamFixedWidthReadTest: 33 tests passed");
    }
}
