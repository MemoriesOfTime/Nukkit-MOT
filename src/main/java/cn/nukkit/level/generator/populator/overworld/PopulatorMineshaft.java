package cn.nukkit.level.generator.populator.overworld;

import cn.nukkit.Server;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.biome.EnumBiome;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.generator.math.BoundingBox;
import cn.nukkit.level.generator.populator.type.Populator;
import cn.nukkit.level.generator.structure.MineshaftPieces;
import cn.nukkit.level.generator.structure.StructureStart;
import cn.nukkit.level.generator.task.CallbackableChunkGenerationTask;
import cn.nukkit.math.NukkitRandom;

public class PopulatorMineshaft extends Populator {
    protected static final int PROBABILITY = 4;
    protected static final boolean[] VALID_BIOMES = new boolean[256];

    static {
        VALID_BIOMES[EnumBiome.OCEAN.id] = true;
        VALID_BIOMES[EnumBiome.PLAINS.id] = true;
        VALID_BIOMES[EnumBiome.DESERT.id] = true;
        VALID_BIOMES[EnumBiome.EXTREME_HILLS.id] = true;
        VALID_BIOMES[EnumBiome.FOREST.id] = true;
        VALID_BIOMES[EnumBiome.TAIGA.id] = true;
        VALID_BIOMES[EnumBiome.SWAMP.id] = true;
        VALID_BIOMES[EnumBiome.RIVER.id] = true;
        VALID_BIOMES[EnumBiome.FROZEN_OCEAN.id] = true;
        VALID_BIOMES[EnumBiome.FROZEN_RIVER.id] = true;
        VALID_BIOMES[EnumBiome.MUSHROOM_ISLAND.id] = true;
        VALID_BIOMES[EnumBiome.MUSHROOM_ISLAND_SHORE.id] = true;
        VALID_BIOMES[EnumBiome.BEACH.id] = true;
        VALID_BIOMES[EnumBiome.DESERT_HILLS.id] = true;
        VALID_BIOMES[EnumBiome.FOREST_HILLS.id] = true;
        VALID_BIOMES[EnumBiome.TAIGA_HILLS.id] = true;
        VALID_BIOMES[EnumBiome.EXTREME_HILLS_EDGE.id] = true;
        VALID_BIOMES[EnumBiome.JUNGLE.id] = true;
        VALID_BIOMES[EnumBiome.JUNGLE_HILLS.id] = true;
        VALID_BIOMES[EnumBiome.JUNGLE_EDGE.id] = true;
        VALID_BIOMES[EnumBiome.DEEP_OCEAN.id] = true;
        VALID_BIOMES[EnumBiome.STONE_BEACH.id] = true;
        VALID_BIOMES[EnumBiome.BIRCH_FOREST.id] = true;
        VALID_BIOMES[EnumBiome.BIRCH_FOREST_HILLS.id] = true;
        VALID_BIOMES[EnumBiome.ROOFED_FOREST.id] = true;
        VALID_BIOMES[EnumBiome.MEGA_TAIGA.id] = true;
        VALID_BIOMES[EnumBiome.MEGA_TAIGA_HILLS.id] = true;
        VALID_BIOMES[EnumBiome.EXTREME_HILLS_PLUS.id] = true;
        VALID_BIOMES[EnumBiome.SAVANNA.id] = true;
        VALID_BIOMES[EnumBiome.SAVANNA_PLATEAU.id] = true;
        VALID_BIOMES[EnumBiome.MESA.id] = true;
        VALID_BIOMES[EnumBiome.MESA_PLATEAU_F.id] = true;
        VALID_BIOMES[EnumBiome.MESA_PLATEAU.id] = true;
        VALID_BIOMES[44] = true; // WARM_OCEAN
        VALID_BIOMES[45] = true; // LUKEWARM_OCEAN
        VALID_BIOMES[47] = true; // DEEP_WARM_OCEAN
        VALID_BIOMES[48] = true; // DEEP_LUKEWARM_OCEAN
        VALID_BIOMES[50] = true; // DEEP_FROZEN_OCEAN
        VALID_BIOMES[EnumBiome.SUNFLOWER_PLAINS.id] = true;
        VALID_BIOMES[EnumBiome.DESERT_M.id] = true;
        VALID_BIOMES[EnumBiome.EXTREME_HILLS_M.id] = true;
        VALID_BIOMES[EnumBiome.FLOWER_FOREST.id] = true;
        VALID_BIOMES[EnumBiome.TAIGA_M.id] = true;
        VALID_BIOMES[EnumBiome.SWAMPLAND_M.id] = true;
        VALID_BIOMES[EnumBiome.JUNGLE_M.id] = true;
        VALID_BIOMES[EnumBiome.JUNGLE_EDGE_M.id] = true;
        VALID_BIOMES[EnumBiome.BIRCH_FOREST_M.id] = true;
        VALID_BIOMES[EnumBiome.BIRCH_FOREST_HILLS_M.id] = true;
        VALID_BIOMES[EnumBiome.ROOFED_FOREST_M.id] = true;
        VALID_BIOMES[EnumBiome.MEGA_SPRUCE_TAIGA.id] = true;
        VALID_BIOMES[161] = true; //GIANT_SPRUCE_TAIGA_HILLS
        VALID_BIOMES[EnumBiome.EXTREME_HILLS_PLUS_M.id] = true;
        VALID_BIOMES[EnumBiome.SAVANNA_M.id] = true;
        VALID_BIOMES[EnumBiome.SAVANNA_PLATEAU_M.id] = true;
        VALID_BIOMES[EnumBiome.MESA_BRYCE.id] = true;
        VALID_BIOMES[EnumBiome.MESA_PLATEAU_F_M.id] = true;
        VALID_BIOMES[EnumBiome.MESA_PLATEAU_M.id] = true;
        VALID_BIOMES[168] = true; //BAMBOO_JUNGLE
        VALID_BIOMES[169] = true; //BAMBOO_JUNGLE_HILLS
    }

    @Override
    public void populate(final ChunkManager level, final int chunkX, final int chunkZ, final NukkitRandom random, final FullChunk chunk) {
        if (VALID_BIOMES[chunk.getBiomeId(7, 7)]) {
            //\\ MineshaftFeature::isFeatureChunk(BiomeSource const &,Random &,ChunkPos const &,uint)
            final long seed = level.getSeed();
            random.setSeed(seed);
            final int r1 = random.nextInt();
            final int r2 = random.nextInt();
            random.setSeed((long) chunkX * r1 ^ (long) chunkZ * r2 ^ seed);

            if (random.nextBoundedInt(1000) < PROBABILITY) {
                //\\ MineshaftFeature::createStructureStart(Dimension &,BiomeSource &,Random &,ChunkPos const &)
                final MineshaftStart start = new MineshaftStart(level, chunkX, chunkZ);
                start.generatePieces(level, chunkX, chunkZ);

                if (start.isValid()) { //TODO: serialize nbt
                    final BoundingBox boundingBox = start.getBoundingBox();
                    for (int cx = boundingBox.x0 >> 4; cx <= boundingBox.x1 >> 4; cx++) {
                        for (int cz = boundingBox.z0 >> 4; cz <= boundingBox.z1 >> 4; cz++) {
                            final NukkitRandom rand = new NukkitRandom((long) cx * r1 ^ (long) cz * r2 ^ seed);
                            final int x = cx << 4;
                            final int z = cz << 4;
                            BaseFullChunk ck = level.getChunk(cx, cz);
                            if (ck == null) {
                                ck = chunk.getProvider().getChunk(cx, cz, true);
                            }

                            if (ck.isGenerated()) {
                                start.postProcess(level, rand, new BoundingBox(x, z, x + 15, z + 15), cx, cz);
                            } else {
                                final int f_cx = cx;
                                final int f_cz = cz;
                                Server.getInstance().getScheduler().scheduleAsyncTask(new CallbackableChunkGenerationTask<>(
                                    chunk.getProvider().getLevel(), ck, start,
                                    structure -> structure.postProcess(level, rand, new BoundingBox(x, z, x + 15, z + 15), f_cx, f_cz)));
                            }
                        }
                    }
                }
            }
        }
    }

    public enum Type {
        NORMAL,
        MESA
    }

    public static class MineshaftStart extends StructureStart {

        public MineshaftStart(final ChunkManager level, final int chunkX, final int chunkZ) {
            super(level, chunkX, chunkZ);
        }

        @Override
        public void generatePieces(final ChunkManager level, final int chunkX, final int chunkZ) {
            final BaseFullChunk chunk = level.getChunk(chunkX, chunkZ);
            if (chunk != null) {
                final int biome = chunk.getBiomeId(7, 7);
                final Type type = biome >= EnumBiome.MESA.id && biome <= EnumBiome.MESA_PLATEAU.id || biome >= EnumBiome.MESA_BRYCE.id && biome <= EnumBiome.MESA_PLATEAU_M.id ? Type.MESA : Type.NORMAL;

                final MineshaftPieces.MineshaftRoom start = new MineshaftPieces.MineshaftRoom(0, random, (chunkX << 4) + 2, (chunkZ << 4) + 2, type);
                pieces.add(start);
                start.addChildren(start, pieces, random);
                calculateBoundingBox();

                if (type == Type.MESA) {
                    moveBelowSeaLevel(64, random, 40);
                } else {
                    moveBelowSeaLevel(64, random, 10);
                }
            }
        }

        @Override //\\ MineshaftStart::getType(void) // 3
        public String getType() {
            return "Mineshaft";
        }
    }
}
