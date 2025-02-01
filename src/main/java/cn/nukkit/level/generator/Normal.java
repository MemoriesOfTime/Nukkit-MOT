package cn.nukkit.level.generator;

import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.block.BlockStone;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.biome.Biome;
import cn.nukkit.level.biome.BiomeSelector;
import cn.nukkit.level.biome.EnumBiome;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.generator.biomegrid.MapLayer;
import cn.nukkit.level.generator.ground.*;
import cn.nukkit.level.generator.noise.bukkit.OctaveGenerator;
import cn.nukkit.level.generator.noise.PerlinOctaveGenerator;
import cn.nukkit.level.generator.noise.SimplexOctaveGenerator;
import cn.nukkit.level.generator.object.ore.OreType;
import cn.nukkit.level.generator.populator.impl.*;
import cn.nukkit.level.generator.populator.impl.PopulatorCaves;
import cn.nukkit.level.generator.populator.impl.PopulatorDeepslate;
import cn.nukkit.level.generator.populator.overworld.*;
import cn.nukkit.level.generator.populator.type.Populator;
import cn.nukkit.level.generator.task.ChunkPopulationTask;
import cn.nukkit.math.MathHelper;
import cn.nukkit.math.NukkitRandom;
import cn.nukkit.math.Vector3;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.ThreadLocalRandom;

import static cn.nukkit.level.generator.util.Vanillaset.TYPE_AMPLIFIED;

/**
 * Nukkit's terrain generator
 * Originally adapted from the PocketMine-MP generator by NycuRO and CreeperFace
 * Mostly rewritten by DaPorkchop_
 * <p>
 * The generator classes, and others related to terrain generation are theirs and are intended for NUKKIT USAGE and should not be copied/translated to other server software
 * such as BukkitPE, ClearSky, Genisys, PocketMine-MP, or others
 */
public class Normal extends Generator {



    protected static final int SEA_LEVEL = 64;
    protected static boolean useGPU;



    /**
     * The biome maps used to fill chunks biome grid and terrain generation.
     */
    protected MapLayer[] biomeGrid;

    protected static final double[][] ELEVATION_WEIGHT = new double[5][5];
    protected static final Int2ObjectMap<GroundGenerator> GROUND_MAP = new Int2ObjectOpenHashMap<>();
    protected static final Int2ObjectMap<BiomeHeight> HEIGHT_MAP = new Int2ObjectOpenHashMap<>();

    protected static double coordinateScale =  684.412d;
    protected static double heightScale = 684.412d;
    protected static double heightNoiseScaleX =  200d; // depthNoiseScaleX
    protected static double heightNoiseScaleZ =  200d; // depthNoiseScaleZ
    protected static double detailNoiseScaleX = 80d;  // mainNoiseScaleX
    protected static double detailNoiseScaleY = 160d; // mainNoiseScaleY
    protected static double detailNoiseScaleZ =  80d;  // mainNoiseScaleZ
    protected static double surfaceScale = 0.0625d;
    protected static double baseSize = 8.5d;
    protected static double stretchY =  12d;
    protected static double biomeHeightOffset =  0d;    // biomeDepthOffset
    protected static double biomeHeightWeight =  1d;    // biomeDepthWeight
    protected static double biomeScaleOffset =  0d;
    protected static double biomeScaleWeight =  1d;

    static {
        setBiomeSpecificGround(new GroundGeneratorSandy(), EnumBiome.BEACH.id, EnumBiome.COLD_BEACH.id, EnumBiome.DESERT.id, EnumBiome.DESERT_HILLS.id, EnumBiome.DESERT_M.id);
        setBiomeSpecificGround(new GroundGeneratorRocky(),  EnumBiome.STONE_BEACH.id);
        setBiomeSpecificGround(new GroundGeneratorSnowy(),  EnumBiome.ICE_PLAINS_SPIKES.id);
        setBiomeSpecificGround(new GroundGeneratorMycel(),  EnumBiome.MUSHROOM_ISLAND.id,  EnumBiome.MUSHROOM_ISLAND_SHORE.id);
        setBiomeSpecificGround(new GroundGeneratorPatchStone(),  EnumBiome.EXTREME_HILLS.id);
        setBiomeSpecificGround(new GroundGeneratorPatchGravel(),  EnumBiome.EXTREME_HILLS_M.id,  EnumBiome.EXTREME_HILLS_PLUS_M.id);
        setBiomeSpecificGround(new GroundGeneratorPatchDirtAndStone(),  EnumBiome.SAVANNA_M.id,  EnumBiome.SAVANNA_PLATEAU_M.id);
        setBiomeSpecificGround(new GroundGeneratorPatchDirt(),  EnumBiome.MEGA_TAIGA.id,  EnumBiome.MEGA_TAIGA_HILLS.id,  EnumBiome.MEGA_SPRUCE_TAIGA.id);//,  EnumBiome.MEGA_SPRUCE_TAIGA_HILLS.id
        setBiomeSpecificGround(new GroundGeneratorMesa(),  EnumBiome.MESA.id,  EnumBiome.MESA_PLATEAU.id,  EnumBiome.MESA_PLATEAU_F.id);
        setBiomeSpecificGround(new GroundGeneratorMesa(GroundGeneratorMesa.MesaType.BRYCE),  EnumBiome.MESA_BRYCE.id);
        setBiomeSpecificGround(new GroundGeneratorMesa(GroundGeneratorMesa.MesaType.FOREST),  EnumBiome.MESA_PLATEAU_F.id,  EnumBiome.MESA_PLATEAU_F_M.id);

        setBiomeHeight(BiomeHeight.OCEAN, EnumBiome.OCEAN.id, EnumBiome.FROZEN_OCEAN.id);
        setBiomeHeight(BiomeHeight.DEEP_OCEAN, EnumBiome.DEEP_OCEAN.id);
        setBiomeHeight(BiomeHeight.RIVER, EnumBiome.RIVER.id, EnumBiome.FROZEN_RIVER.id);
        setBiomeHeight(BiomeHeight.FLAT_SHORE, EnumBiome.BEACH.id, EnumBiome.COLD_BEACH.id, EnumBiome.MUSHROOM_ISLAND_SHORE.id);
        setBiomeHeight(BiomeHeight.ROCKY_SHORE, EnumBiome.STONE_BEACH.id);
        setBiomeHeight(BiomeHeight.FLATLANDS, EnumBiome.DESERT.id, EnumBiome.ICE_PLAINS.id, EnumBiome.SAVANNA.id);
        setBiomeHeight(BiomeHeight.EXTREME_HILLS, EnumBiome.EXTREME_HILLS.id, EnumBiome.EXTREME_HILLS_PLUS.id, EnumBiome.EXTREME_HILLS_M.id, EnumBiome.EXTREME_HILLS_PLUS_M.id);
        setBiomeHeight(BiomeHeight.MID_PLAINS, EnumBiome.TAIGA.id, EnumBiome.COLD_TAIGA.id, EnumBiome.MEGA_TAIGA.id);
        setBiomeHeight(BiomeHeight.SWAMPLAND, EnumBiome.SWAMP.id);
        setBiomeHeight(BiomeHeight.LOW_HILLS, EnumBiome.MUSHROOM_ISLAND.id);
        setBiomeHeight(BiomeHeight.HILLS, EnumBiome.DESERT_HILLS.id, EnumBiome.FOREST_HILLS.id, EnumBiome.TAIGA_HILLS.id, EnumBiome.EXTREME_HILLS_EDGE.id, EnumBiome.JUNGLE_HILLS.id, EnumBiome.BIRCH_FOREST_HILLS.id, EnumBiome.COLD_TAIGA_HILLS.id, EnumBiome.MEGA_TAIGA_HILLS.id, EnumBiome.MESA_PLATEAU_F_M.id, EnumBiome.MESA_PLATEAU_M.id);//, EnumBiome.ICE_MOUNTAINS.id
        setBiomeHeight(BiomeHeight.HIGH_PLATEAU, EnumBiome.SAVANNA_PLATEAU.id, EnumBiome.MESA_PLATEAU_F.id, EnumBiome.MESA_PLATEAU.id);
        setBiomeHeight(BiomeHeight.FLATLANDS_HILLS, EnumBiome.DESERT_M.id);
        setBiomeHeight(BiomeHeight.BIG_HILLS, EnumBiome.ICE_PLAINS_SPIKES.id);
        setBiomeHeight(BiomeHeight.BIG_HILLS2, EnumBiome.BIRCH_FOREST_HILLS_M.id);
        setBiomeHeight(BiomeHeight.SWAMPLAND_HILLS, EnumBiome.SWAMPLAND_M.id);
        setBiomeHeight(BiomeHeight.DEFAULT_HILLS, EnumBiome.JUNGLE_M.id, EnumBiome.JUNGLE_EDGE_M.id, EnumBiome.BIRCH_FOREST_M.id, EnumBiome.ROOFED_FOREST_M.id);
        setBiomeHeight(BiomeHeight.MID_HILLS, EnumBiome.TAIGA_M.id, EnumBiome.COLD_TAIGA_M.id, EnumBiome.MEGA_SPRUCE_TAIGA.id);//, EnumBiome.MEGA_SPRUCE_TAIGA_HILLS.id
        setBiomeHeight(BiomeHeight.MID_HILLS2, EnumBiome.FLOWER_FOREST.id);
        setBiomeHeight(BiomeHeight.LOW_SPIKES, EnumBiome.SAVANNA_M.id);
        setBiomeHeight(BiomeHeight.HIGH_SPIKES, EnumBiome.SAVANNA_PLATEAU_M.id);

        // fill a 5x5 array with values that acts as elevation weight on chunk neighboring, this can be viewed as a parabolic field: the center gets the more weight, and the weight decreases as distance increases from the center. This is applied on the lower scale biome grid.
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                int sqX = x - 2;
                sqX *= sqX;
                int sqZ = z - 2;
                sqZ *= sqZ;
                ELEVATION_WEIGHT[x][z] = 10d / Math.sqrt(sqX + sqZ + 0.2d);
            }
        }
    }




    protected final Map<String, Map<String, OctaveGenerator>> octaveCache = Maps.newHashMap();
    protected final double[][][] density = new double[5][5][33];
    protected final GroundGenerator groundGen = new GroundGenerator();
    protected final BiomeHeight defaultHeight = BiomeHeight.DEFAULT;

    protected static void setBiomeSpecificGround(GroundGenerator gen, int... biomes) {
        for (int biome : biomes) {
            GROUND_MAP.put(biome, gen);
        }
    }

    protected static void setBiomeHeight(BiomeHeight height, int... biomes) {
        for (int biome : biomes) {
            HEIGHT_MAP.put(biome, height);
        }
    }

    protected List<Populator> generationPopulators = Lists.newArrayList();
    protected List<Populator> populators = Lists.newArrayList();
    protected ChunkManager level;
    protected NukkitRandom nukkitRandom;
    protected long localSeed1;
    protected long localSeed2;

    public Normal() {
        // reflect
    }

    public Normal(Map<String, Object> options) {
        // reflect
    }

    @Override
    public int getId() {
        return TYPE_INFINITE;
    }

    @Override
    public ChunkManager getChunkManager() {
        return level;
    }

    @Override
    public String getName() {
        return "normal";
    }


    public static final int BEDROCK_LAYER = -64;

    private static final float[] biomeWeights = new float[25];

    static {
        for (int i = -2; i <= 2; ++i) {
            for (int j = -2; j <= 2; ++j) {
                biomeWeights[i + 2 + (j + 2) * 5] = (float) (10.0F / Math.sqrt((float) (i * i + j * j) + 0.2F));
            }
        }
    }


    private List<Populator> structurePopulators = ImmutableList.of(
            new PopulatorFossil(),
            new PopulatorShipwreck(),
            new PopulatorSwampHut(),
            new PopulatorDesertPyramid(),
            new PopulatorJungleTemple(),
            new PopulatorIgloo(),
            new PopulatorPillagerOutpost(),
            new PopulatorOceanRuin(),
            new PopulatorVillage(),
            new PopulatorStronghold(),
            new PopulatorMineshaft(),
            new PopulatorDesertWell(),
            new PopulatorDungeon()
    );
    public static final int seaHeight = 64; // should be 62


    private BiomeSelector selector;
    private ThreadLocal<float[]> depthRegion = ThreadLocal.withInitial(() -> null);
    private ThreadLocal<float[]> mainNoiseRegion = ThreadLocal.withInitial(() -> null);
    private ThreadLocal<float[]> minLimitRegion = ThreadLocal.withInitial(() -> null);
    private ThreadLocal<float[]> maxLimitRegion = ThreadLocal.withInitial(() -> null);
    private ThreadLocal<float[]> heightMap = ThreadLocal.withInitial(() -> new float[825]);




    @Override
    public Map<String, Object> getSettings() {
        return Collections.emptyMap();
    }

    public Biome pickBiome(int x, int z) {
        return this.selector.pickBiome(x, z);
    }

    @Override
    public void init(ChunkManager level, NukkitRandom random) {
        this.level = level;
        this.nukkitRandom = random;
        this.nukkitRandom.setSeed(this.level.getSeed());
        this.localSeed1 = ThreadLocalRandom.current().nextLong();
        this.localSeed2 = ThreadLocalRandom.current().nextLong();
        this.nukkitRandom.setSeed(this.level.getSeed());

        this.generationPopulators = ImmutableList.of(new PopulatorCaves());

        this.populators = ImmutableList.of(
                new PopulatorOre(STONE, new OreType[]{
                        new OreType(Block.get(COAL_ORE), 20, 17, 0, 128),
                        new OreType(Block.get(IRON_ORE), 20, 9, 0, 64),
                        new OreType(Block.get(REDSTONE_ORE), 8, 8, 0, 16),
                        new OreType(Block.get(LAPIS_ORE), 1, 7, 0, 16),
                        new OreType(Block.get(GOLD_ORE), 2, 9, 0, 32),
                        new OreType(Block.get(DIAMOND_ORE), 1, 8, 0, 16),
                        new OreType(Block.get(DIRT), 10, 33, 0, 128),
                        new OreType(Block.get(GRAVEL), 8, 33, 0, 128),
                        new OreType(Block.get(STONE, BlockStone.GRANITE), 10, 33, 0, 80),
                        new OreType(Block.get(STONE, BlockStone.DIORITE), 10, 33, 0, 80),
                        new OreType(Block.get(STONE, BlockStone.ANDESITE), 10, 33, 0, 80)
                }),

                new PopulatorSnowLayers()
        );
        this.biomeGrid = MapLayer.initialize(level.getSeed(), this.getDimension(), this.getId());
    }

    @Override
    public void populateStructure(final int chunkX, final int chunkZ) {
        final BaseFullChunk chunk = level.getChunk(chunkX, chunkZ);
        for (final Populator populator : structurePopulators) {
            Server.getInstance().computeThreadPool.submit(new ChunkPopulationTask(level, chunk, populator));
        }
    }

    @Override
    public void generateChunk(int chunkX, int chunkZ) {
        this.nukkitRandom.setSeed(chunkX * localSeed1 ^ chunkZ * localSeed2 ^ this.level.getSeed());

        BaseFullChunk chunkData = level.getChunk(chunkX, chunkZ);

        // Scaling chunk x and z coordinates (4x, see below)
        int x = chunkX << 2;
        int z = chunkZ << 2;

        // Get biome grid data at lower res (scaled 4x, at this scale a chunk is 4x4 columns of the biome grid), we are loosing biome detail but saving huge amount of computation.
        // We need 1 chunk (4 columns) + 1 column for later needed outer edges (1 column) and at least 2 columns on each side to be able to cover every value.
        // 4 + 1 + 2 + 2 = 9 columns but the biomegrid generator needs a multiple of 2 so we ask 10 columns wide to the biomegrid generator.
        // This gives a total of 81 biome grid columns to work with, and this includes the chunk neighborhood.
        int[] biomeGrid = this.biomeGrid[1].generateValues(x - 2, z - 2, 10, 10);

        Map<String, OctaveGenerator> octaves = getWorldOctaves();
        double[] heightNoise = ((PerlinOctaveGenerator) octaves.get("height")).getFractalBrownianMotion(x, z, 0.5d, 2d);
        double[] roughnessNoise = ((PerlinOctaveGenerator) octaves.get("roughness")).getFractalBrownianMotion(x, 0, z, 0.5d, 2d);
        double[] roughnessNoise2 = ((PerlinOctaveGenerator) octaves.get("roughness2")).getFractalBrownianMotion(x, 0, z, 0.5d, 2d);
        double[] detailNoise = ((PerlinOctaveGenerator) octaves.get("detail")).getFractalBrownianMotion(x, 0, z, 0.5d, 2d);

        int index = 0;
        int indexHeight = 0;

        // Sampling densities.
        // Ideally we would sample 512 (4x4x32) values but in reality we need 825 values (5x5x33).
        // This is because linear interpolation is done later to re-scale so we need right and bottom edge values if we want it to be "seamless".
        // You can check this picture to have a visualization of how the biomegrid is traversed (2D plan): http://i.imgur.com/s4whlZE.png
        // The big square grid represents our lower res biomegrid columns, and the very small square grid represents the normal biome grid columns (at block level) and the reason why it's required to re-scale it and do linear interpolation before densities can be used to generate raw terrain.
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                double avgHeightScale = 0;
                double avgHeightBase = 0;
                double totalWeight = 0;
                int biome = Biome.getBiome(biomeGrid[i + 2 + (j + 2) * 10]).getId();
                BiomeHeight biomeHeight = HEIGHT_MAP.getOrDefault(biome, defaultHeight);
                // Sampling an average height base and scale by visiting the neighborhood of the current biomegrid column.
                for (int m = 0; m < 5; m++) {
                    for (int n = 0; n < 5; n++) {
                        int nearBiome = Biome.getBiome(biomeGrid[i + m + (j + n) * 10]).getId();
                        BiomeHeight nearBiomeHeight = HEIGHT_MAP.getOrDefault(nearBiome, defaultHeight);
                        double heightBase = biomeHeightOffset + nearBiomeHeight.getHeight() * biomeHeightWeight;
                        double heightScale = biomeScaleOffset + nearBiomeHeight.getScale() * biomeScaleWeight;
                        if (this.getId() == TYPE_AMPLIFIED && heightBase > 0) {
                            heightBase = 1d + heightBase * 2d;
                            heightScale = 1d + heightScale * 4d;
                        }
                        double weight = ELEVATION_WEIGHT[m][n] / (heightBase + 2d);
                        if (nearBiomeHeight.getHeight() > biomeHeight.getHeight()) {
                            weight *= 0.5d;
                        }
                        avgHeightScale += heightScale * weight;
                        avgHeightBase += heightBase * weight;
                        totalWeight += weight;
                    }
                }
                avgHeightScale /= totalWeight;
                avgHeightBase /= totalWeight;
                avgHeightScale = avgHeightScale * 0.9d + 0.1d;
                avgHeightBase = (avgHeightBase * 4d - 1d) / 8d;

                double noiseH = heightNoise[indexHeight++] / 8000d;
                if (noiseH < 0) {
                    noiseH = Math.abs(noiseH) * 0.3d;
                }
                noiseH = noiseH * 3d - 2d;
                if (noiseH < 0) {
                    noiseH = Math.max(noiseH * 0.5d, -1) / 1.4d * 0.5d;
                } else {
                    noiseH = Math.min(noiseH, 1) / 8d;
                }

                noiseH = (noiseH * 0.2d + avgHeightBase) * baseSize / 8d * 4d + baseSize;
                for (int k = 0; k < 33; k++) {
                    // density should be lower and lower as we climb up, this gets a height value to subtract from the noise.
                    double nh = (k - noiseH) * stretchY * 128d / 256d / avgHeightScale;
                    if (nh < 0) {
                        nh *= 4d;
                    }
                    double noiseR = roughnessNoise[index] / 512d;
                    double noiseR2 = roughnessNoise2[index] / 512d;
                    double noiseD = (detailNoise[index] / 10d + 1d) / 2d;
                    // linear interpolation
                    double dens = noiseD < 0 ? noiseR
                            : noiseD > 1 ? noiseR2 : noiseR + (noiseR2 - noiseR) * noiseD;
                    dens -= nh;
                    index++;
                    if (k > 29) {
                        double lowering = (k - 29) / 3d;
                        // linear interpolation
                        dens = dens * (1d - lowering) + -10d * lowering;
                    }
                    this.density[i][j][k] = dens;
                }
            }
        }

        // Terrain densities are sampled at different resolutions (1/4x on x,z and 1/8x on y by default) so it's needed to re-scale it. Linear interpolation is used to fill in the gaps.



        int fill = 0;
        int afill = Math.abs(fill);
        int seaFill =  0;
        double densityOffset =  0d;

        for (int i = 0; i < 5 - 1; i++) {
            for (int j = 0; j < 5 - 1; j++) {
                for (int k = 0; k < 33 - 1; k++) {
                    // 2x2 grid
                    double d1 = this.density[i][j][k];
                    double d2 = this.density[i + 1][j][k];
                    double d3 = this.density[i][j + 1][k];
                    double d4 = this.density[i + 1][j + 1][k];
                    // 2x2 grid (row above)
                    double d5 = (this.density[i][j][k + 1] - d1) / 8;
                    double d6 = (this.density[i + 1][j][k + 1] - d2) / 8;
                    double d7 = (this.density[i][j + 1][k + 1] - d3) / 8;
                    double d8 = (this.density[i + 1][j + 1][k + 1] - d4) / 8;

                    for (int l = 0; l < 8; l++) {
                        double d9 = d1;
                        double d10 = d3;
                        for (int m = 0; m < 4; m++) {
                            double dens = d9;
                            for (int n = 0; n < 4; n++) {
                                // any density higher than density offset is ground, any density lower or equal to the density offset is air (or water if under the sea level).
                                // this can be flipped if the mode is negative, so lower or equal to is ground, and higher is air/water and, then data can be shifted by afill the order is air by default, ground, then water.
                                // they can shift places within each if statement the target is densityOffset + 0, since the default target is 0, so don't get too confused by the naming.
                                if (afill == 1 || afill == 10 || afill == 13 || afill == 16) {
                                    chunkData.setBlock(m + (i << 2), l + (k << 3), n + (j << 2), STILL_WATER);
                                } else if (afill == 2 || afill == 9 || afill == 12 || afill == 15) {
                                    chunkData.setBlock(m + (i << 2), l + (k << 3), n + (j << 2), STONE);
                                }
                                if (dens > densityOffset && fill > -1 || dens <= densityOffset && fill < 0) {
                                    if (afill == 0 || afill == 3 || afill == 6 || afill == 9 || afill == 12) {
                                        chunkData.setBlock(m + (i << 2), l + (k << 3), n + (j << 2), STONE);
                                    } else if (afill == 2 || afill == 7 || afill == 10 || afill == 16) {
                                        chunkData.setBlock(m + (i << 2), l + (k << 3), n + (j << 2), STILL_WATER);
                                    }
                                } else if (l + (k << 3) < SEA_LEVEL - 1 && seaFill == 0 || l + (k << 3) >= SEA_LEVEL - 1 && seaFill == 1) {
                                    if (afill == 0 || afill == 3 || afill == 7 || afill == 10 || afill == 13) {
                                        chunkData.setBlock(m + (i << 2), l + (k << 3), n + (j << 2), STILL_WATER);
                                    } else if (afill == 1 || afill == 6 || afill == 9 || afill == 15) {
                                        chunkData.setBlock(m + (i << 2), l + (k << 3), n + (j << 2), STONE);
                                    }
                                }
                                // interpolation along z
                                dens += (d10 - d9) / 4;
                            }
                            // interpolation along x
                            d9 += (d2 - d1) / 4;
                            // interpolate along z
                            d10 += (d4 - d3) / 4;
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

        int cx = chunkX << 4;
        int cz = chunkZ << 4;

        BiomeGrid biomes = new BiomeGrid();
        int[] biomeValues = this.biomeGrid[0].generateValues(cx, cz, 16, 16);
        for (int i = 0; i < biomeValues.length; i++) {
            biomes.biomes[i] = (byte) biomeValues[i];
        }

        PerlinOctaveGenerator octaveGenerator = (PerlinOctaveGenerator) getWorldOctaves().get("surface");

        int sizeX = octaveGenerator.getSizeX();
        int sizeZ = octaveGenerator.getSizeZ();

         {
            double[] surfaceNoise = octaveGenerator.getFractalBrownianMotion(cx, cz, 0.5d, 0.5d);
            for (int sx = 0; sx < sizeX; sx++) {
                for (int sz = 0; sz < sizeZ; sz++) {
                    GROUND_MAP.getOrDefault(biomes.getBiome(sx, sz), groundGen).generateTerrainColumn(level, chunkData, this.nukkitRandom, cx + sx, cz + sz, biomes.getBiome(sx, sz), surfaceNoise[sx | sz << 4]);
                    chunkData.setBiomeId(sx, sz, biomes.getBiome(sx, sz));
                }
            }
        }

        //populate chunk
        this.generationPopulators.forEach(populator -> populator.populate(this.level, chunkX, chunkZ, this.nukkitRandom, chunkData));
    }


    protected static class BiomeGrid {

        public final byte[] biomes = new byte[256];

        public int getBiome(int x, int z) {
            // upcasting is very important to get extended biomes
            return Biome.biomes[biomes[x | z << 4] & 0xff].getId();
        }

        public void setBiome(int x, int z, int bio) {
            biomes[x | z << 4] = (byte) Biome.biomes[bio].getId();
        }
    }

    @Override
    public void populateChunk(int chunkX, int chunkZ) {
        this.nukkitRandom.setSeed(0xdeadbeef ^ (chunkX << 8) ^ chunkZ ^ this.level.getSeed());
        for (Populator populator : this.populators) {
            populator.populate(this.level, chunkX, chunkZ, this.nukkitRandom, level.getChunk(chunkX, chunkZ));
        }

        Biome biome = EnumBiome.getBiome(level.getChunk(chunkX, chunkZ).getBiomeId(7, 7));
        biome.populateChunk(this.level, chunkX, chunkZ, this.nukkitRandom);
    }

    @Override
    public Vector3 getSpawn() {
        return new Vector3(0.5, 256, 0.5);
    }

    protected static class BiomeHeight {

        public static final BiomeHeight DEFAULT = new BiomeHeight(0.1d, 0.2d);
        public static final BiomeHeight FLAT_SHORE = new BiomeHeight(0d, 0.025d);
        public static final BiomeHeight HIGH_PLATEAU = new BiomeHeight(1.5d, 0.025d);
        public static final BiomeHeight FLATLANDS = new BiomeHeight(0.125d, 0.05d);
        public static final BiomeHeight SWAMPLAND = new BiomeHeight(-0.2d, 0.1d);
        public static final BiomeHeight MID_PLAINS = new BiomeHeight(0.2d, 0.2d);
        public static final BiomeHeight FLATLANDS_HILLS = new BiomeHeight(0.275d, 0.25d);
        public static final BiomeHeight SWAMPLAND_HILLS = new BiomeHeight(-0.1d, 0.3d);
        public static final BiomeHeight LOW_HILLS = new BiomeHeight(0.2d, 0.3d);
        public static final BiomeHeight HILLS = new BiomeHeight(0.45d, 0.3d);
        public static final BiomeHeight MID_HILLS2 = new BiomeHeight(0.1d, 0.4d);
        public static final BiomeHeight DEFAULT_HILLS = new BiomeHeight(0.2d, 0.4d);
        public static final BiomeHeight MID_HILLS = new BiomeHeight(0.3d, 0.4d);
        public static final BiomeHeight BIG_HILLS = new BiomeHeight(0.525d, 0.55d);
        public static final BiomeHeight BIG_HILLS2 = new BiomeHeight(0.55d, 0.5d);
        public static final BiomeHeight EXTREME_HILLS = new BiomeHeight(1d, 0.5d);
        public static final BiomeHeight ROCKY_SHORE = new BiomeHeight(0.1d, 0.8d);
        public static final BiomeHeight LOW_SPIKES = new BiomeHeight(0.4125d, 1.325d);
        public static final BiomeHeight HIGH_SPIKES = new BiomeHeight(1.1d, 1.3125d);
        public static final BiomeHeight RIVER = new BiomeHeight(-0.5d, 0d);
        public static final BiomeHeight OCEAN = new BiomeHeight(-1d, 0.1d);
        public static final BiomeHeight DEEP_OCEAN = new BiomeHeight(-1.8d, 0.1d);
        protected final double height;
        protected final double scale;

        BiomeHeight(double height, double scale){
            this.height = height;
            this.scale = scale;
        }

        public double getHeight(){
            return this.height;
        }

        public double getScale(){
            return this.scale;
        }
    }


    protected Map<String, OctaveGenerator> getWorldOctaves() {
        Map<String, OctaveGenerator> octaves = this.octaveCache.get(this.getName());
        if (octaves == null) {
            octaves = Maps.newHashMap();
            NukkitRandom seed = new NukkitRandom(this.level.getSeed());

            OctaveGenerator gen = new PerlinOctaveGenerator(seed, 16, 5, 5);
            gen.setXScale(heightNoiseScaleX);
            gen.setZScale(heightNoiseScaleZ);
            octaves.put("height", gen);

            gen = new PerlinOctaveGenerator(seed, 16, 5, 33, 5);
            gen.setXScale(coordinateScale);
            gen.setYScale(heightScale);
            gen.setZScale(coordinateScale);
            octaves.put("roughness", gen);

            gen = new PerlinOctaveGenerator(seed, 16, 5, 33, 5);
            gen.setXScale(coordinateScale);
            gen.setYScale(heightScale);
            gen.setZScale(coordinateScale);
            octaves.put("roughness2", gen);

            gen = new PerlinOctaveGenerator(seed, 8, 5, 33, 5);
            gen.setXScale(coordinateScale / detailNoiseScaleX);
            gen.setYScale(heightScale / detailNoiseScaleY);
            gen.setZScale(coordinateScale / detailNoiseScaleZ);
            octaves.put("detail", gen);

            gen = new SimplexOctaveGenerator(seed, 4, 16, 16);
            gen.setScale(surfaceScale);
            octaves.put("surface", gen);

            this.octaveCache.put(this.getName(), octaves);
        }
        return octaves;
    }
}