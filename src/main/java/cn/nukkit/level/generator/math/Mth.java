package cn.nukkit.level.generator.math;

import cn.nukkit.math.NukkitRandom;

public final class Mth {
    public static int nextInt(final NukkitRandom random, final int origin, final int bound) {
        return origin >= bound ? origin : random.nextBoundedInt(bound - origin + 1) + origin;
    }

    public static int getSeed(int x, int y, int z) {
        long xord = (x * 0x2fc20fL) ^ z * 0x6ebfff5L ^ (long) y;
        return (int) ((xord * 0x285b825 + 0xb) * xord);
    }
}
