package cn.nukkit.utils;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XXHash64 规范测试向量与内部一致性检查。
 * <p>
 * XXHash64 spec test vectors plus internal consistency checks.
 */
public class XXHash64Test {

    // 官方广泛引用的向量 / widely-cited official vectors (xxHash spec)
    @Test
    public void specVectors() {
        assertEquals(0xEF46DB3751D8E999L, XXHash64.hash(new byte[0]), "empty, seed 0");
        assertEquals(0xD24EC4F1A98C6E5BL, XXHash64.hash("a".getBytes()), "\"a\", seed 0");
        assertEquals(0x44BC2CF5AD770999L, XXHash64.hash("abc".getBytes()), "\"abc\", seed 0");
    }

    @Test
    public void offsetLengthMatchesSlicedCopy() {
        byte[] data = new byte[257];
        new Random(42).nextBytes(data);
        for (int off : new int[]{0, 1, 7, 31, 32, 33, 64, 100}) {
            for (int len : new int[]{0, 1, 3, 7, 15, 16, 31, 32, 33, 63, 64, 65, 128}) {
                if (off + len > data.length) {
                    continue;
                }
                long whole = XXHash64.hash(0, data, off, len);
                long sliced = XXHash64.hash(Arrays.copyOfRange(data, off, off + len));
                assertEquals(whole, sliced, "off=" + off + " len=" + len);
            }
        }
    }

    @Test
    public void avalancheOnSingleBitFlip() {
        byte[] data = new byte[100];
        new Random(7).nextBytes(data);
        long base = XXHash64.hash(data);
        long distinct = 0;
        long last = 0;
        for (int i = 0; i < 8; i++) {
            data[i / 8] ^= (byte) (1 << (i % 8));
            long h = XXHash64.hash(data);
            assertNotEquals(base, h, "bit " + i + " flip must change the hash");
            if (i > 0) {
                distinct |= (last ^ h) == 0 ? 1 : 0;
            }
            last = h;
            data[i / 8] ^= (byte) (1 << (i % 8));
        }
        assertEquals(0, distinct, "different bit flips must not all collide");
    }

    @Test
    public void rejectsBadRange() {
        byte[] data = new byte[8];
        assertThrows(Exception.class, () -> XXHash64.hash(0, data, 4, 8));
        assertThrows(Exception.class, () -> XXHash64.hash(0, data, -1, 2));
        assertThrows(Exception.class, () -> XXHash64.hash(0, data, 0, -1));
    }
}
