package cn.nukkit.math;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class NukkitMath {

    public static final float PI = (float) Math.PI;
    public static final float TWO_PI = PI * 2;
    public static final float HALF_PI = PI / 2;
    public static final float DEG_TO_RAD = PI / 180;
    public static final float RAD_TO_DEG = 180 / PI;
    public static final float E = (float) Math.E;
    public static final float EPSILON = 1.0E-5f;
    public static final float EPSILON_NORMAL_SQRT = 1.0E-15f;
    public static final float SQRT_OF_TWO = (float) Math.sqrt(2);
    public static final float SQRT_OF_THREE = (float) Math.sqrt(3);
    private static final double LOG_OF_TWO = Math.log(2);

    private static final float[] SIN = new float[65536];
    private static final int[] MULTIPLY_DE_BRUIJN_BIT_POSITION = new int[]{
            0, 1, 28, 2, 29, 14, 24, 3, 30, 22, 20, 15, 25, 17, 4, 8,
            31, 27, 13, 23, 21, 19, 16, 7, 26, 12, 18, 6, 11, 5, 10, 9,
    };
    private static final double FRAC_BIAS = Double.longBitsToDouble(0x42b0000000000000L);
    private static final int LUT_SIZE = 257;
    private static final double[] ASIN_TAB = new double[LUT_SIZE];
    private static final double[] COS_TAB = new double[LUT_SIZE];

    static {
        for (int i = 0; i < SIN.length; i++) {
            SIN[i] = (float) Math.sin(i * Math.PI * 2 / 65536);
        }
        for (int i = 0; i < LUT_SIZE; i++) {
            double asin = Math.asin(i / 256.0);
            COS_TAB[i] = Math.cos(asin);
            ASIN_TAB[i] = asin;
        }
    }

    public static float sin(float v) {
        return SIN[(int) (v * 10430.378f) & Character.MAX_VALUE];
    }

    public static double sin(double v) {
        return SIN[(int) (v * 10430.378) & Character.MAX_VALUE];
    }

    public static float cos(float v) {
        return SIN[(int) (v * 10430.378f + 16384) & Character.MAX_VALUE];
    }

    public static double cos(double v) {
        return SIN[(int) (v * 10430.378 + 16384) & Character.MAX_VALUE];
    }

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

    public static double atan2(double dy, double dx) {
        double square = dx * dx + dy * dy;

        if (Double.isNaN(square)) {
            return Double.NaN;
        }

        boolean ny = dy < 0;
        if (ny) {
            dy = -dy;
        }

        boolean nx = dx < 0;
        if (nx) {
            dx = -dx;
        }

        boolean yg = dy > dx;
        if (yg) {
            double t = dx;
            dx = dy;
            dy = t;
        }

        double s = fastInvSqrt(square);
        dx *= s;
        dy *= s;
        double b = FRAC_BIAS + dy;
        int i = (int) Double.doubleToRawLongBits(b);
        double asin = ASIN_TAB[i];
        double e = dy * COS_TAB[i] - dx * (b - FRAC_BIAS);
        double r = asin + (6 + e * e) * e * (1 / 6d);

        if (yg) {
            r = Math.PI / 2 - r;
        }
        if (nx) {
            r = Math.PI - r;
        }
        if (ny) {
            r = -r;
        }
        return r;
    }

    static float fastInvSqrt(float v) {
        float h = 0.5f * v;
        int i = Float.floatToIntBits(v);
        i = 0x5f375a86 - (i >> 1);
        v = Float.intBitsToFloat(i);
        v *= 1.5f - h * v * v;
        return v;
    }

    static double fastInvSqrt(double v) {
        double h = 0.5 * v;
        long l = Double.doubleToRawLongBits(v);
        l = 0x5fe6eb50c7b537a9L - (l >> 1);
        v = Double.longBitsToDouble(l);
        v *= 1.5 - h * v * v;
        return v;
    }
}
