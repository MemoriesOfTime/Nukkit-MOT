package cn.nukkit.level.generator.noise;

import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.Level;
import cn.nukkit.level.generator.noise.bukkit.NoiseGenerator;
import cn.nukkit.level.generator.noise.bukkit.OctaveGenerator;
import cn.nukkit.level.generator.noise.bukkit.SimplexNoiseGenerator;
import cn.nukkit.math.NukkitRandom;

import java.util.Arrays;

/**
 * Creates simplex noise through unbiased octaves
 */
public class SimplexOctaveGenerator extends PerlinOctaveGenerator {

    public SimplexOctaveGenerator(NukkitRandom rand, int octaves, int sizeX, int sizeZ) {
        this(rand, octaves, sizeX, 1, sizeZ);
    }

    public SimplexOctaveGenerator(NukkitRandom rand, int octaves, int sizeX, int sizeY, int sizeZ) {
        super(createOctaves(rand, octaves), rand, sizeX, sizeY, sizeZ);
    }

    public SimplexOctaveGenerator(NukkitRandom rand, int octaves) {
        this(rand, octaves, 0, 0, 0);
    }

    public static NoiseGenerator[] createOctaves(NukkitRandom rand, int octaves) {
        NoiseGenerator[] result = new NoiseGenerator[octaves];

        for (int i = 0; i < octaves; i++) {
            result[i] = new SimplexNoise(rand);
        }

        return result;
    }

    @Override
    public double[] getFractalBrownianMotion(double x, double y, double z, double lacunarity, double persistence) {
        Arrays.fill(noise, 0);

        double freq = 1;
        double amp = 1;

        // fBm
        for (NoiseGenerator octave : octaves) {
            noise = ((PerlinNoise) octave).getNoise(noise, x, y, z, sizeX, sizeY, sizeZ, xScale * freq, yScale * freq, zScale * freq, 0.55D / amp);
            freq *= lacunarity;
            amp *= persistence;
        }

        return noise;
    }
}


