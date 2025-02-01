package cn.nukkit.level.generator.noise.bukkit;

import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.Level;
import cn.nukkit.math.NukkitRandom;

/**
 * Creates simplex noise through unbiased octaves
 */
public class SimplexOctaveGenerator extends OctaveGenerator {

    private double wScale = 1;

    /**
     * Creates a simplex octave generator for the given world
     *
     * @param world World to construct this generator for
     * @param octaves Amount of octaves to create
     */
    public SimplexOctaveGenerator(Level world, int octaves) {
        this(new NukkitRandom(world.getSeed()), octaves);
    }

    /**
     * Creates a simplex octave generator for the given level
     *
     * @param level Level to construct this generator for
     * @param octaves Amount of octaves to create
     */
    public SimplexOctaveGenerator(ChunkManager level, int octaves) {
        this(new NukkitRandom(level.getSeed()), octaves);
    }

    /**
     * Creates a simplex octave generator for the given world
     *
     * @param seed Seed to construct this generator for
     * @param octaves Amount of octaves to create
     */
    public SimplexOctaveGenerator(long seed, int octaves) {
        this(new NukkitRandom(seed), octaves);
    }

    /**
     * Creates a simplex octave generator for the given {@link NukkitRandom}
     *
     * @param rand NukkitRandom object to construct this generator for
     * @param octaves Amount of octaves to create
     */
    public SimplexOctaveGenerator(NukkitRandom rand, int octaves) {
        super(createOctaves(rand, octaves));
    }
    @Override
    public void setScale(double scale) {
        super.setScale(scale);
        this.setWScale(scale);
    }

    /**
     * Gets the scale used for each W-coordinates passed
     *
     * @return W scale
     */
    public double getWScale() {
        return this.wScale;
    }

    /**
     * Sets the scale used for each W-coordinates passed
     *
     * @param scale New W scale
     */
    public void setWScale(double scale) {
        this.wScale = scale;
    }

    /**
     * Generates noise for the 3D coordinates using the specified number of
     * octaves and parameters
     *
     * @param x X-coordinate
     * @param y Y-coordinate
     * @param z Z-coordinate
     * @param w W-coordinate
     * @param frequency How much to alter the frequency by each octave
     * @param amplitude How much to alter the amplitude by each octave
     * @return Resulting noise
     */
    public double noise(double x, double y, double z, double w, double frequency, double amplitude) {
        return this.noise(x, y, z, w, frequency, amplitude, false);
    }

    /**
     * Generates noise for the 3D coordinates using the specified number of
     * octaves and parameters
     *
     * @param x X-coordinate
     * @param y Y-coordinate
     * @param z Z-coordinate
     * @param w W-coordinate
     * @param frequency How much to alter the frequency by each octave
     * @param amplitude How much to alter the amplitude by each octave
     * @param normalized If true, normalize the value to [-1, 1]
     * @return Resulting noise
     */
    public double noise(double x, double y, double z, double w, double frequency, double amplitude, boolean normalized) {
        double result = 0;
        double amp = 1;
        double freq = 1;
        double max = 0;

        x *= this.xScale;
        y *= this.yScale;
        z *= this.zScale;
        w *= this.wScale;

        for (NoiseGenerator octave : this.octaves) {
            result += ((SimplexNoiseGenerator) octave).noise(x * freq, y * freq, z * freq, w * freq) * amp;
            max += amp;
            freq *= frequency;
            amp *= amplitude;
        }

        if (normalized) {
            result /= max;
        }

        return result;
    }

    private static NoiseGenerator[] createOctaves(NukkitRandom rand, int octaves) {
        NoiseGenerator[] result = new NoiseGenerator[octaves];

        for (int i = 0; i < octaves; ++i) {
            result[i] = new SimplexNoiseGenerator(rand);
        }

        return result;
    }


}
