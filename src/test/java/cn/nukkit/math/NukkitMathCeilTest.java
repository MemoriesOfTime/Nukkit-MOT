package cn.nukkit.math;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class NukkitMathCeilTest {
    @Test
    void doubleCeilingMatchesMathForFractionsAndAdjacentIntegers() {
        double[] values = {Double.NaN, 1.25, 0.25, -0.25, -1.25, 0.0, -0.0, 1.0, -1.0,
                Math.nextDown(1.0), Math.nextUp(1.0), Math.nextDown(-1.0),
                Math.nextUp(-1.0), Double.MIN_VALUE, -Double.MIN_VALUE,
                Math.nextUp((double) Integer.MIN_VALUE), Math.nextDown((double) Integer.MAX_VALUE)};
        for (double value : values) {
            assertEquals((int) Math.ceil(value), NukkitMath.ceilDouble(value), "input=" + value);
        }
        Random random = new Random(27_09_2026);
        for (int i = 0; i < 10_000; i++) {
            double value = random.nextDouble() * 4_000_000_000.0 - 2_000_000_000.0;
            assertEquals((int) Math.ceil(value), NukkitMath.ceilDouble(value), "input=" + value);
        }
    }

    @Test
    void doubleInputOverflowStillThrows() {
        for (double value : new double[]{Integer.MIN_VALUE, Integer.MAX_VALUE,
                Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, -Double.MAX_VALUE, Double.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> NukkitMath.ceilDouble(value));
        }
    }

    @Test
    void floatCeilingMatchesMathWithoutOverflowAtLargeMagnitudes() {
        float[] values = {Float.NaN, 1.25f, 0.25f, -0.25f, -1.25f, 0.0f, -0.0f, 1.0f, -1.0f,
                Math.nextDown(1.0f), Math.nextUp(1.0f), Math.nextDown(-1.0f), Math.nextUp(-1.0f),
                Float.MIN_VALUE, -Float.MIN_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE,
                Integer.MIN_VALUE, Integer.MAX_VALUE, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY};
        for (float value : values) {
            assertEquals((int) Math.ceil(value), NukkitMath.ceilFloat(value), "input=" + value);
        }
        Random random = new Random(27_09_2026);
        for (int i = 0; i < 10_000; i++) {
            float value = Float.intBitsToFloat(random.nextInt());
            if (Float.isNaN(value)) continue;
            assertEquals((int) Math.ceil(value), NukkitMath.ceilFloat(value), "input=" + value);
        }
    }
}
