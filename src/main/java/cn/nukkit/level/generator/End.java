package cn.nukkit.level.generator;

import cn.nukkit.block.Block;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.Level;
import cn.nukkit.level.biome.Biome;
import cn.nukkit.level.biome.EnumBiome;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.generator.noise.vanilla.d.NoiseGeneratorOctavesD;
import cn.nukkit.level.generator.noise.vanilla.d.NoiseGeneratorSimplexD;
import cn.nukkit.level.generator.populator.impl.*;
import cn.nukkit.level.generator.populator.type.Populator;
import cn.nukkit.math.MathHelper;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.math.NukkitRandom;
import cn.nukkit.math.Vector3;

import java.util.*;

/**
 * Created by PetteriM1
 */
public class End extends Generator {

    private static final double coordinateScale = 684.412;
    private static final double detailNoiseScaleX = 80;
    private static final double detailNoiseScaleZ = 80;
    private final double[][][] density = new double[3][3][33];
    double[] detailNoise;
    double[] roughnessNoise;
    double[] roughnessNoise2;
    private ChunkManager level;
    private NukkitRandom nukkitRandom;
    private Random random;
    private NoiseGeneratorOctavesD roughnessNoiseOctaves;
    private NoiseGeneratorOctavesD roughnessNoiseOctaves2;
    private NoiseGeneratorOctavesD detailNoiseOctaves;
    private NoiseGeneratorSimplexD islandNoise;
    private final List<Populator> populators = new ArrayList<>();
    private final List<Populator> generationPopulators = new ArrayList<>();

    private long localSeed1;
    private long localSeed2;

    public End() {
        this(new HashMap<>());
    }

    public End(Map<String, Object> options) {
    }

    @Override
    public int getId() {
        return Generator.TYPE_THE_END;
    }

    @Override
    public int getDimension() {
        return Level.DIMENSION_THE_END;
    }

    @Override
    public ChunkManager getChunkManager() {
        return level;
    }

    @Override
    public Map<String, Object> getSettings() {
        return new HashMap<>();
    }

    @Override
    public String getName() {
        return "the_end";
    }

    @Override
    public void init(ChunkManager level, NukkitRandom random) {
        this.level = level;
        this.nukkitRandom = random;
        this.random = new Random();
        this.nukkitRandom.setSeed(this.level.getSeed());
        this.localSeed1 = this.random.nextLong();
        this.localSeed2 = this.random.nextLong();

        this.roughnessNoiseOctaves = new NoiseGeneratorOctavesD(random, 16);
        this.roughnessNoiseOctaves2 = new NoiseGeneratorOctavesD(random, 16);
        this.detailNoiseOctaves = new NoiseGeneratorOctavesD(random, 8);
        this.islandNoise = new NoiseGeneratorSimplexD(random);

        this.populators.add(new PopulatorEndPlatform());
        this.populators.add(new PopulatorEndIsland(this));
        this.populators.add(new PopulatorChorusTree(this));
        this.populators.add(new PopulatorEndGateway(this));

        PopulatorEndObsidianPillar.ObsidianPillar[] obsidianPillars = PopulatorEndObsidianPillar.ObsidianPillar.getObsidianPillars(this.level.getSeed());
        for (PopulatorEndObsidianPillar.ObsidianPillar obsidianPillar : obsidianPillars) {
            PopulatorEndObsidianPillar populator = new PopulatorEndObsidianPillar(obsidianPillar);
            this.populators.add(populator);
        }
    }

    @Override
    public void populateStructure(final int chunkX, final int chunkZ) {
    }

    @Override
    public void generateChunk(int chunkX, int chunkZ) {
        this.nukkitRandom.setSeed(chunkX * localSeed1 ^ chunkZ * localSeed2 ^ this.level.getSeed());

        BaseFullChunk chunk = level.getChunk(chunkX, chunkZ);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                Biome biome = EnumBiome.END.biome;
                chunk.setBiomeId(x, z, biome.getId());
            }
        }

        int densityX = chunkX << 1;
        int densityZ = chunkZ << 1;

        this.detailNoise = this.detailNoiseOctaves.generateNoiseOctaves(this.detailNoise, densityX, 0, densityZ, 3, 33,
                3, (coordinateScale * 2) / detailNoiseScaleX, 4.277575000000001,
                (coordinateScale * 2) / detailNoiseScaleZ);
        this.roughnessNoise = this.roughnessNoiseOctaves.generateNoiseOctaves(this.roughnessNoise, densityX, 0,
                densityZ, 3, 33, 3, coordinateScale * 2, coordinateScale, coordinateScale * 2);
        this.roughnessNoise2 = this.roughnessNoiseOctaves2.generateNoiseOctaves(this.roughnessNoise2, densityX, 0,
                densityZ, 3, 33, 3, coordinateScale * 2, coordinateScale, coordinateScale * 2);

        int index = 0;

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                float noiseHeight = this.getIslandHeight(chunkX, chunkZ, i, j);
                for (int k = 0; k < 33; k++) {
                    double noiseR = this.roughnessNoise[index] / 512d;
                    double noiseR2 = this.roughnessNoise2[index] / 512d;
                    double noiseD = (this.detailNoise[index] / 10d + 1d) / 2d;
                    // linear interpolation
                    double dens = noiseD < 0 ? noiseR : noiseD > 1 ? noiseR2 : noiseR + (noiseR2 - noiseR) * noiseD;
                    dens = (dens - 8d) + (double) noiseHeight;
                    index++;
                    double lowering;
                    if (k < 8) {
                        lowering = (float) (8 - k) / 7;
                        dens = dens * (1d - lowering) + lowering * -30d;
                    } else if (k > 33 / 2 - 2) {
                        lowering = (float) (k - ((33 / 2) - 2)) / 64d;
                        lowering = NukkitMath.clamp(lowering, 0, 1);
                        dens = dens * (1d - lowering) + lowering * -3000d;
                    }
                    density[i][j][k] = dens;
                }
            }
        }

        for (int i = 0; i < 3 - 1; i++) {
            for (int j = 0; j < 3 - 1; j++) {
                for (int k = 0; k < 33 - 1; k++) {
                    double d1 = density[i][j][k];
                    double d2 = density[i + 1][j][k];
                    double d3 = density[i][j + 1][k];
                    double d4 = density[i + 1][j + 1][k];
                    double d5 = (density[i][j][k + 1] - d1) / 4;
                    double d6 = (density[i + 1][j][k + 1] - d2) / 4;
                    double d7 = (density[i][j + 1][k + 1] - d3) / 4;
                    double d8 = (density[i + 1][j + 1][k + 1] - d4) / 4;

                    for (int l = 0; l < 4; l++) {
                        double d9 = d1;
                        double d10 = d3;
                        for (int m = 0; m < 8; m++) {
                            double dens = d9;
                            for (int n = 0; n < 8; n++) {
                                // any density > 0 is ground, any density <= 0 is air.
                                if (dens > 0) {
                                    chunk.setBlockId(m + (i << 3), l + (k << 2), n + (j << 3), Block.END_STONE);
                                }
                                // interpolation along z
                                dens += (d10 - d9) / 8;
                            }
                            // interpolation along x
                            d9 += (d2 - d1) / 8;
                            // interpolate along z
                            d10 += (d4 - d3) / 8;
                        }
                        // interpolation along y
                        d1 += d5;
                        d3 += d7;
                        d2 += d6;
                        d4 += d8;
                    }
                }
            }
        }

        for (Populator populator : this.generationPopulators) {
            populator.populate(this.level, chunkX, chunkZ, this.nukkitRandom, chunk);
        }
    }

    @Override
    public void populateChunk(int chunkX, int chunkZ) {
        BaseFullChunk chunk = level.getChunk(chunkX, chunkZ);
        this.nukkitRandom.setSeed(0xdeadbeef ^ ((long) chunkX << 8) ^ chunkZ ^ this.level.getSeed());
        Biome biome = EnumBiome.getBiome(chunk.getBiomeId(7, 7));
        for (Populator populator : this.populators) {
            populator.populate(this.level, chunkX, chunkZ, this.nukkitRandom, chunk);
        }
        biome.populateChunk(this.level, chunkX, chunkZ, this.nukkitRandom);
    }

    @Override
    public Vector3 getSpawn() {
        return new Vector3(0, 64, 0);
    }

    public float getIslandHeight(int chunkX, int chunkZ, int x, int z) {
        float x1 = (float) (chunkX * 2 + x);
        float z1 = (float) (chunkZ * 2 + z);
        float islandHeight1 = NukkitMath.clamp(100f - MathHelper.sqrt((x1 * x1) + (z1 * z1)) * 8f, -100f, 80f);

        for (int i = -12; i <= 12; i++) {
            for (int j = -12; j <= 12; j++) {
                long x2 = chunkX + i;
                long z2 = chunkZ + j;
                if ((x2 * x2) + (z2 * z2) > 4096L
                        && this.islandNoise.getValue((double) x2, (double) z2) < (double) -0.9f) {
                    x1 = (float) (x - i * 2);
                    z1 = (float) (z - j * 2);
                    float islandHeight2 = 100f - MathHelper.sqrt((x1 * x1) + (z1 * z1))
                            * ((Math.abs((float) x2) * 3439f + Math.abs((float) z2) * 147f) % 13f + 9f);
                    islandHeight2 = NukkitMath.clamp(islandHeight2, -100f, 80f);
                    islandHeight1 = Math.max(islandHeight1, islandHeight2);
                }
            }
        }

        return islandHeight1;
    }
}
