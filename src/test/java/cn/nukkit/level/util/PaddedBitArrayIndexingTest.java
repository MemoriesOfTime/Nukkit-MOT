package cn.nukkit.level.util;

import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class PaddedBitArrayIndexingTest {

    private static volatile long allocationChecksum;

    @ParameterizedTest
    @EnumSource(BitArrayVersion.class)
    void reciprocalMatchesDivisionForEveryUnsignedShort(BitArrayVersion version) {
        for (int index = 0; index <= 65535; index++) {
            int expected = version.entriesPerWord == 0 ? 0 : index / version.entriesPerWord;
            assertEquals(expected, version.getWordIndex(index));
        }
    }

    @ParameterizedTest
    @EnumSource(value = BitArrayVersion.class, mode = EnumSource.Mode.EXCLUDE, names = "V0")
    void reciprocalKeepsFullNonnegativeIntRange(BitArrayVersion version) {
        int divisor = version.entriesPerWord;
        for (int index = Integer.MAX_VALUE - 65535; index < Integer.MAX_VALUE; index++) {
            assertEquals(index / divisor, version.getWordIndex(index));
        }
        assertEquals(Integer.MAX_VALUE / divisor, version.getWordIndex(Integer.MAX_VALUE));
        int random = 0x6A09E667;
        for (int i = 0; i < 65536; i++) {
            random = random * 1664525 + 1013904223;
            int index = random & Integer.MAX_VALUE;
            assertEquals(index / divisor, version.getWordIndex(index));
        }
    }

    @ParameterizedTest
    @EnumSource(BitArrayVersion.class)
    void allCellsAndWordsMatchDivisionBasedLayout(BitArrayVersion version) {
        BitArray actual = version.createPalette(65536);
        int[] expectedWords = new int[version.getWordsForSize(actual.size())];
        int mask = version.getMaxEntryValue();
        for (int index = 0; index < actual.size(); index++) {
            int value = valueAt(index, mask);
            actual.set(index, value);
            if (version != BitArrayVersion.V0) {
                int word = index / version.entriesPerWord;
                int offset = (index % version.entriesPerWord) * version.bits;
                expectedWords[word] = expectedWords[word] & ~(mask << offset) | value << offset;
            }
        }
        assertArrayEquals(expectedWords, actual.getWords());
        for (int index = 0; index < actual.size(); index++) {
            assertEquals(valueAt(index, mask), actual.get(index));
        }

        BitArray copy = actual.copy();
        assertArrayEquals(expectedWords, copy.getWords());
        for (int index = actual.size() - 1; index >= 0; index--) {
            actual.set(index, mask ^ valueAt(index, mask));
        }
        for (int index = 0; index < actual.size(); index++) {
            assertEquals(mask ^ valueAt(index, mask), actual.get(index));
            assertEquals(valueAt(index, mask), copy.get(index));
        }
    }

    @ParameterizedTest
    @EnumSource(value = BitArrayVersion.class, names = {"V3", "V5", "V6"})
    void partialLastWordsKeepPaddingAndCopiedStorage(BitArrayVersion version) {
        int[] sizes = {0, 1, 2, 3, 4095, 4096, 4097, 65535, 65536};
        for (int size : sizes) {
            int[] words = new int[version.getWordsForSize(size)];
            java.util.Arrays.fill(words, -1);
            BitArray actual = version.createPalette(size, words);
            for (int index = 0; index < size; index++) {
                actual.set(index, 0);
            }
            for (int index = 0; index < size; index++) {
                assertEquals(0, actual.get(index));
            }
            for (int word = 0; word < words.length; word++) {
                int entries = Math.min(version.entriesPerWord, size - word * version.entriesPerWord);
                assertEquals(-1 << (entries * version.bits), words[word]);
            }
            assertArrayEquals(words, actual.copy().getWords());
        }
    }

    @ParameterizedTest
    @EnumSource(value = BitArrayVersion.class, names = {"V3", "V5", "V6"})
    void invalidIndexesAndValuesKeepValidationOrder(BitArrayVersion version) {
        BitArray actual = version.createPalette(4096);
        assertThrows(IndexOutOfBoundsException.class, () -> actual.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> actual.get(4096));
        assertThrows(IndexOutOfBoundsException.class, () -> actual.get(Integer.MAX_VALUE));
        assertThrows(IndexOutOfBoundsException.class, () -> actual.set(-1, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> actual.set(4096, -1));
        assertThrows(IllegalArgumentException.class, () -> actual.set(0, -1));
        assertThrows(IllegalArgumentException.class, () -> actual.set(0, version.getMaxEntryValue() + 1));
        assertEquals(0, actual.get(0));
        assertThrows(IllegalArgumentException.class,
                () -> version.createPalette(4096, new int[version.getWordsForSize(4096) - 1]));
    }

    @Test
    void emptyPaletteStillRejectsEveryReadAndWrite() {
        for (BitArrayVersion version : BitArrayVersion.values()) {
            BitArray actual = version.createPalette(0);
            assertThrows(IndexOutOfBoundsException.class, () -> actual.get(0));
            assertThrows(IndexOutOfBoundsException.class, () -> actual.set(0, 0));
        }
    }

    @Test
    void paddedReadsAndWritesDoNotAllocate(@TempDir Path directory) throws Exception {
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        BitArray[] arrays = paddedArrays();
        for (int round = 0; round < 64; round++) {
            exercise(arrays, 4096);
        }
        long before = bean.getCurrentThreadAllocatedBytes();
        exercise(arrays, 4096);
        if (bean.getCurrentThreadAllocatedBytes() - before > 4096) {
            // Inline mocking can add allocations to every get/set in a reused test fork.
            String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
            Path output = directory.resolve("padded-allocation.log");
            Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-cp", classPath, PaddedBitArrayIndexingTest.class.getName())
                    .redirectErrorStream(true).redirectOutput(output.toFile()).start();
            try {
                assertTrue(child.waitFor(30, TimeUnit.SECONDS), "Child allocation probe timed out");
                assertEquals(0, child.exitValue(), Files.readString(output, StandardCharsets.UTF_8));
            } finally {
                if (child.isAlive()) {
                    child.destroyForcibly();
                    assertTrue(child.waitFor(10, TimeUnit.SECONDS));
                }
            }
        } else {
            verifyNoAllocations(bean, arrays);
        }
    }

    public static void main(String[] args) {
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        BitArray[] arrays = paddedArrays();
        for (int round = 0; round < 64; round++) {
            exercise(arrays, 4096);
        }
        verifyNoAllocations(bean, arrays);
    }

    private static void verifyNoAllocations(ThreadMXBean bean, BitArray[] arrays) {
        long before = bean.getCurrentThreadAllocatedBytes();
        exercise(arrays, 262144);
        long allocated = bean.getCurrentThreadAllocatedBytes() - before;
        assertTrue(allocated < 1024, "Padded get/set allocated " + allocated + " bytes");
    }

    private static BitArray[] paddedArrays() {
        return new BitArray[]{BitArrayVersion.V3.createPalette(), BitArrayVersion.V5.createPalette(),
                BitArrayVersion.V6.createPalette()};
    }

    private static void exercise(BitArray[] arrays, int count) {
        long checksum = 0;
        for (BitArray array : arrays) {
            int mask = array.getVersion().getMaxEntryValue();
            for (int i = 0; i < count; i++) {
                int index = i & 4095;
                array.set(index, i & mask);
                checksum += array.get(index);
            }
        }
        allocationChecksum = checksum;
    }

    private static int valueAt(int index, int mask) {
        return ((index * 0x9E3779B9) ^ (index >>> 3)) & mask;
    }
}
