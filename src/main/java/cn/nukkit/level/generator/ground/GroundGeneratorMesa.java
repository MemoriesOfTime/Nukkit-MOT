package cn.nukkit.level.generator.ground;

import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.generator.noise.bukkit.SimplexOctaveGenerator;
import cn.nukkit.math.NukkitRandom;


import java.util.Arrays;

public class GroundGeneratorMesa extends GroundGenerator {

    private final MesaType type;
    private final int[] colorLayer = new int[64];
    private SimplexOctaveGenerator colorNoise;
    private SimplexOctaveGenerator canyonHeightNoise;
    private SimplexOctaveGenerator canyonScaleNoise;
    private long seed;

    public GroundGeneratorMesa() {
        this(MesaType.NORMAL);
    }

    /**
     * Creates a ground generator for mesa biomes.
     *
     * @param type the type of mesa biome to generate
     */
    public GroundGeneratorMesa(MesaType type) {
        this.type = type;
    }

    private void initialize(long seed) {
        if (seed != this.seed || this.colorNoise == null || this.canyonScaleNoise == null || this.canyonHeightNoise == null) {
            NukkitRandom random = new NukkitRandom(seed);
            this.colorNoise = new SimplexOctaveGenerator(random, 1);
            this.colorNoise.setScale(1 / 512.0D);
            this.initializeColorLayers(random);

            this.canyonHeightNoise = new SimplexOctaveGenerator(random, 4);
            this.canyonHeightNoise.setScale(1 / 4.0D);
            this.canyonScaleNoise = new SimplexOctaveGenerator(random, 1);
            this.canyonScaleNoise.setScale(1 / 512.0D);
            this.seed = seed;
        }
    }

    @Override
    public void generateTerrainColumn(ChunkManager world, BaseFullChunk chunkData, NukkitRandom random, int chunkX, int chunkZ, int biome, double surfaceNoise) {
        this.initialize(world.getSeed());

        int surfaceHeight = Math.max((int) (surfaceNoise / 3.0D + 3.0D + random.nextDouble() * 0.25D), 1);
        boolean colored = Math.cos(surfaceNoise / 3.0D * Math.PI) <= 0;
        double bryceCanyonHeight = 0;
        if (type == MesaType.BRYCE) {
            int noiseX = (chunkX & 0xFFFFFFF0) + (chunkZ & 0xF);
            int noiseZ = (chunkZ & 0xFFFFFFF0) + (chunkX & 0xF);
            double noiseCanyonHeight = Math.min(Math.abs(surfaceNoise), this.canyonHeightNoise.noise(noiseX, noiseZ, 0.5D, 2.0D));
            if (noiseCanyonHeight > 0) {
                double heightScale = Math.abs(this.canyonScaleNoise.noise(noiseX, noiseZ, 0.5D, 2.0D));
                bryceCanyonHeight = Math.pow(noiseCanyonHeight, 2) * 2.5D;
                double maxHeight = Math.ceil(50 * heightScale) + 14;
                if (bryceCanyonHeight > maxHeight) {
                    bryceCanyonHeight = maxHeight;
                }
                bryceCanyonHeight += SEA_LEVEL;
            }
        }

        int x = chunkX & 0xF;
        int z = chunkZ & 0xF;

        int deep = -1;
        boolean groundSet = false;
        for (int y = 255; y >= 0; y--) {
            if (y < (int) bryceCanyonHeight && chunkData.getBlockId(x, y, z) == AIR) {
                chunkData.setBlock(x, y, z, STONE);
            }
            if (y <= random.nextBoundedInt(5)) {
                chunkData.setBlock(x, y, z, BEDROCK);
            } else {
                int mat = chunkData.getBlockId(x, y, z);
                if (mat == AIR) {
                    deep = -1;
                } else if (mat == STONE) { // revert 9747d77 -- hennick
                    if (deep == -1) {
                        groundSet = false;
                        //if (y >= SEA_LEVEL - 5 && y <= SEA_LEVEL) {
                        //    groundMat = this.groundMaterial;
                        //}

                        deep = surfaceHeight + Math.max(0, y - SEA_LEVEL - 1);
                        if (y >= SEA_LEVEL - 2) {
                            if (type == MesaType.FOREST && y > SEA_LEVEL + 22 + (surfaceHeight << 1)) {
                                int topMat = colored ? GRASS : DIRT;
                                int topData = colored ? 0 : 1;
                                chunkData.setBlock(x, y, z, topMat, topData);
                            } else if (y > SEA_LEVEL + 2 + surfaceHeight) {
                                int color = this.colorLayer[(y + (int) Math.round(this.colorNoise.noise(chunkX, chunkZ, 0.5D, 2.0D) * 2.0D)) % this.colorLayer.length];
                                this.setColoredGroundLayer(chunkData, x, y, z, y < SEA_LEVEL || y > 128 ? 1 : colored ? color : -1);
                            } else {
                                chunkData.setBlock(x, y, z, SAND, 1);
                                groundSet = true;
                            }
                        } else {
                            chunkData.setBlock(x, y, z, STAINED_HARDENED_CLAY, 1);
                        }
                    } else if (deep > 0) {
                        deep--;
                        if (groundSet) {
                            chunkData.setBlock(x, y, z, STAINED_HARDENED_CLAY, 1);
                        } else {
                            int color = this.colorLayer[(y + (int) Math.round(this.colorNoise.noise(chunkX, chunkZ, 0.5D, 2.0D) * 2.0D)) % this.colorLayer.length];
                            this.setColoredGroundLayer(chunkData, x, y, z, color);
                        }
                    }
                }
            }
        }
    }

    private void setColoredGroundLayer(BaseFullChunk chunkData, int x, int y, int z, int color) {
        if (color >= 0) {
            chunkData.setBlock(x, y, z, STAINED_HARDENED_CLAY, color & 0xf);
        } else {
            chunkData.setBlock(x, y, z, TERRACOTTA);
        }
    }

    private void setRandomLayerColor(NukkitRandom random, int minLayerCount, int minLayerHeight, int color) {
        for (int i = 0; i < random.nextBoundedInt(4) + minLayerCount; i++) {
            int j = random.nextBoundedInt(this.colorLayer.length);
            int k = 0;
            while (k < random.nextBoundedInt(3) + minLayerHeight && j < this.colorLayer.length) {
                this.colorLayer[j++] = color;
                k++;
            }
        }
    }

    private void initializeColorLayers(NukkitRandom random) {
        Arrays.fill(this.colorLayer, -1); // hard clay, other values are stained clay
        int i = 0;
        while (i < this.colorLayer.length) {
            i += random.nextBoundedInt(5) + 1;
            if (i < this.colorLayer.length) {
                this.colorLayer[i++] = 1; // orange
            }
        }
        this.setRandomLayerColor(random, 2, 1, 4); // yellow
        this.setRandomLayerColor(random, 2, 2, 12); // brown
        this.setRandomLayerColor(random, 2, 1, 14); // red
        int j = 0;
        for (i = 0; i < random.nextBoundedInt(3) + 3; i++) {
            j += random.nextBoundedInt(16) + 4;
            if (j >= this.colorLayer.length) {
                break;
            }
            if (random.nextBoundedInt(2) == 0 || j < this.colorLayer.length - 1 && random.nextBoundedInt(2) == 0) {
                this.colorLayer[j - 1] = 8; // light gray
            } else {
                this.colorLayer[j] = 0; // white
            }
        }
    }

    public enum MesaType {
        NORMAL,
        BRYCE,
        FOREST
    }
}
