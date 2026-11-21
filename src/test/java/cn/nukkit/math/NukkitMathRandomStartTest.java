package cn.nukkit.math;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NukkitMathRandomStartTest {
    private static final class FixedRandom extends NukkitRandom {
        private final int value;
        private int calls;
        FixedRandom(int value) { super(123); this.value = value; }
        @Override public int nextInt() { calls++; return value; }
        @Override public int nextSignedInt() { calls++; return value; }
    }
    @Test void includesRequestedLowerBound() {
        for (int start : new int[]{1, 7, 1024, Integer.MAX_VALUE}) {
            FixedRandom random = new FixedRandom(0);
            assertEquals(start, NukkitMath.randomRange(random, start));
            assertEquals(1, random.calls);
        }
    }
    @Test void maximumLowerBoundHasOnePossibleResult() {
        assertEquals(Integer.MAX_VALUE, NukkitMath.randomRange(new FixedRandom(42), Integer.MAX_VALUE));
    }
    @Test void fullSignedStartDomainDoesNotOverflowRangeWidth() {
        for (int start : new int[]{Integer.MIN_VALUE, -100, -1, 0, 1, Integer.MAX_VALUE - 1, Integer.MAX_VALUE}) {
            for (int value : new int[]{0, 1, 123456789, Integer.MAX_VALUE}) {
                int result = NukkitMath.randomRange(new FixedRandom(value), start);
                assertEquals((int) (start + value % ((long) Integer.MAX_VALUE - start + 1)), result);
                assertTrue(result >= start);
            }
        }
    }
    @Test void negativeStartsCanReachTheUpperBoundWithoutAnExtraDraw() {
        for (int start : new int[]{Integer.MIN_VALUE, -100, -1}) {
            FixedRandom random = new FixedRandom((int) ((long) Integer.MAX_VALUE - start));
            assertEquals(Integer.MAX_VALUE, NukkitMath.randomRange(random, start));
            assertEquals(1, random.calls);
        }
    }
    @Test void positiveRangesRetainThreeArgumentSequence() {
        for (int start : new int[]{0, 1, 19, 1024, Integer.MAX_VALUE}) {
            NukkitRandom a = new NukkitRandom(987654), b = new NukkitRandom(987654);
            for (int i = 0; i < 100; i++)
                assertEquals(NukkitMath.randomRange(a, start, Integer.MAX_VALUE), NukkitMath.randomRange(b, start));
        }
    }
    @Test void zeroArgumentSequenceIsUnchanged() {
        NukkitRandom a = new NukkitRandom(4321), b = new NukkitRandom(4321);
        for (int i = 0; i < 100; i++) assertEquals(a.nextInt(), NukkitMath.randomRange(b));
    }
}
