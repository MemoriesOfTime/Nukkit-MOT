package cn.nukkit.level.generator.noise.bukkit;

import cn.nukkit.level.generator.noise.PerlinNoise;
import cn.nukkit.math.NukkitRandom;


import java.util.Arrays;

public class PerlinOctaveGenerator extends OctaveGenerator {

    protected final int sizeX;
    protected final int sizeY;
    protected final int sizeZ;
    protected double[] noise;

    public PerlinOctaveGenerator(NukkitRandom rand, int octaves, int sizeX, int sizeZ) {
        this(rand, octaves, sizeX, 1, sizeZ);
    }

    public PerlinOctaveGenerator(NukkitRandom rand, int octaves, int sizeX, int sizeY, int sizeZ) {
        this(createOctaves(rand, octaves), rand, sizeX, sizeY, sizeZ);
    }

    /**
     * Creates a generator for multiple layers of Perlin noise.
     *
     * @param octaves the noise generators
     * @param rand the PRNG
     * @param sizeX the size on the X axis
     * @param sizeY the size on the Y axis
     * @param sizeZ the size on the Z axis
     */
    public PerlinOctaveGenerator(NoiseGenerator[] octaves, NukkitRandom rand, int sizeX, int sizeY, int sizeZ) {
        super(octaves);
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        noise = new double[sizeX * sizeY * sizeZ];
    }

    protected static NoiseGenerator[] createOctaves(NukkitRandom rand, int octaves) {
        NoiseGenerator[] result = new NoiseGenerator[octaves];

        for (int i = 0; i < octaves; i++) {
            result[i] = new PerlinNoise(rand);
        }

        return result;
    }

    protected static long floor(double x) {
        return x >= 0 ? (long) x : (long) x - 1;
    }

    /**
     * Generates multiple layers of noise.
     *
     * @param x the starting X coordinate
     * @param z the starting Z coordinate
     * @param lacunarity layer n's frequency as a fraction of layer
     * {@code n - 1}'s frequency
     * @param persistence layer n's amplitude as a multiple of layer
     * {@code n - 1}'s amplitude
     * @return The noise array
     */
    public double[] getFractalBrownianMotion(double x, double z, double lacunarity, double persistence) {
        return getFractalBrownianMotion(x, 0, z, lacunarity, persistence);
    }

    /**
     * Generates multiple layers of noise.
     *
     * @param x the starting X coordinate
     * @param y the starting Y coordinate
     * @param z the starting Z coordinate
     * @param lacunarity layer n's frequency as a fraction of layer
     * {@code n - 1}'s frequency
     * @param persistence layer n's amplitude as a multiple of layer
     * {@code n - 1}'s amplitude
     * @return The noise array
     */
    public double[] getFractalBrownianMotion(double x, double y, double z, double lacunarity, double persistence) {
        Arrays.fill(noise, 0);

        double freq = 1;
        double amp = 1;

        x *= xScale;
        y *= yScale;
        z *= zScale;

        // fBm
        // the noise have to be periodic over x and z axis: otherwise it can go crazy with high
        // input, leading to strange oddities in terrain generation like the old minecraft farland
        // symptoms.
        for (NoiseGenerator octave : octaves) {
            double dx = x * freq;
            double dz = z * freq;
            // compute integer part
            long lx = floor(dx);
            long lz = floor(dz);
            // compute fractional part
            dx -= lx;
            dz -= lz;
            // wrap integer part to 0..16777216
            lx %= 16777216;
            lz %= 16777216;
            // add to fractional part
            dx += lx;
            dz += lz;

            double dy = y * freq;
            noise = ((PerlinNoise) octave).getNoise(noise, dx, dy, dz, sizeX, sizeY, sizeZ, xScale * freq, yScale * freq, zScale * freq, amp);
            freq *= lacunarity;
            amp *= persistence;
        }

        return noise;
    }

    public int getSizeX() {
        return this.sizeX;
    }

    public int getSizeY() {
        return this.sizeY;
    }

    public int getSizeZ() {
        return this.sizeZ;
    }
}
