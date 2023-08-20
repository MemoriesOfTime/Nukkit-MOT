package cn.nukkit.level.generator.math;

import cn.nukkit.math.NukkitRandom;

public final class Mth {
	public static int nextInt(final NukkitRandom random, final int origin, final int bound) {
		return origin >= bound ? origin : random.nextBoundedInt(bound - origin + 1) + origin;
	}
}
