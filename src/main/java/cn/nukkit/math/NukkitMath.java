package cn.nukkit.math;

import java.math.BigInteger;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class NukkitMath {

    public static int floorDouble(double n) {
        int i = (int) n;
        return n >= i ? i : i - 1;
    }

    public static int ceilDouble(double n) {
        int i = (int) (n + 1);
        return n >= i ? i : i - 1;
    }

    public static int floorFloat(float n) {
        int i = (int) n;
        return n >= i ? i : i - 1;
    }

    public static int ceilFloat(float n) {
        int i = (int) (n + 1);
        return n >= i ? i : i - 1;
    }

    public static int randomRange(NukkitRandom random) {
        return randomRange(random, 0);
    }

    public static int randomRange(NukkitRandom random, int start) {
        return randomRange(random, 0, 0x7fffffff);
    }

    public static int randomRange(NukkitRandom random, int start, int end) {
        return start + (random.nextInt() % (end + 1 - start));
    }

    public static double round(double d) {
        return round(d, 0);
    }

    public static double round(double d, int precision) {
        return ((double) Math.round(d * Math.pow(10, precision))) / Math.pow(10, precision);
    }

    public static float clamp(float value, float min, float max) {
        return value < min ? min : (Math.min(value, max));
    }

    public static double clamp(double value, double min, double max) {
        return value < min ? min : (Math.min(value, max));
    }

    public static int clamp(int value, int min, int max) {
        return value < min ? min : (Math.min(value, max));
    }

    public static double getDirection(double diffX, double diffZ) {
        diffX = Math.abs(diffX);
        diffZ = Math.abs(diffZ);

        return Math.max(diffX, diffZ);
    }

    public static int bitLength(byte data) {
        if (data < 0) {
            return 32;
        }

        if (data == 0) {
            return 1;
        }

        int bits = 0;
        while (data != 0) {
            data >>>= 1;
            bits++;
        }

        return bits;
    }

    public static int bitLength(int data) {
        if (data < 0) {
            return 32;
        }

        if (data == 0) {
            return 1;
        }

        int bits = 0;
        while (data != 0) {
            data >>>= 1;
            bits++;
        }

        return bits;
    }

    public static int bitLength(long data) {
        if (data < 0) {
            return 64;
        }

        if (data == 0) {
            return 1;
        }

        int bits = 0;
        while (data != 0) {
            data >>>= 1;
            bits++;
        }

        return bits;
    }

    public static int bitLength(BigInteger data) {
        if (data.compareTo(BigInteger.ZERO) < 0) {
            throw new UnsupportedOperationException("Negative BigIntegers are not supported (nearly infinite bits)");
        }

        return data.bitLength();
    }
}
