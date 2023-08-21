package cn.nukkit.level.generator.populator.overworld;

import cn.nukkit.Server;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.Level;
import cn.nukkit.level.biome.EnumBiome;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.generator.math.BoundingBox;
import cn.nukkit.level.generator.populator.type.Populator;
import cn.nukkit.level.generator.structure.StrongholdPieces;
import cn.nukkit.level.generator.structure.StructurePiece;
import cn.nukkit.level.generator.structure.StructureStart;
import cn.nukkit.level.generator.task.CallbackableChunkGenerationTask;
import cn.nukkit.math.NukkitRandom;

import java.util.List;

public class PopulatorStronghold extends Populator {
    protected static final int DISTANCE = 6;
    protected static final int COUNT = 32;
    public static final long[] strongholdPos = new long[COUNT];
    protected static final int SPREAD = 3;
    protected static final boolean[] VALID_BIOMES = new boolean[256];

    static {
        VALID_BIOMES[EnumBiome.PLAINS.id] = true;
        VALID_BIOMES[EnumBiome.DESERT.id] = true;
        VALID_BIOMES[EnumBiome.EXTREME_HILLS.id] = true;
        VALID_BIOMES[EnumBiome.FOREST.id] = true;
        VALID_BIOMES[EnumBiome.TAIGA.id] = true;
        VALID_BIOMES[EnumBiome.ICE_PLAINS.id] = true;
        VALID_BIOMES[EnumBiome.MUSHROOM_ISLAND.id] = true;
        VALID_BIOMES[EnumBiome.MUSHROOM_ISLAND_SHORE.id] = true;
        VALID_BIOMES[EnumBiome.DESERT_HILLS.id] = true;
        VALID_BIOMES[EnumBiome.FOREST_HILLS.id] = true;
        VALID_BIOMES[EnumBiome.TAIGA_HILLS.id] = true;
        VALID_BIOMES[EnumBiome.EXTREME_HILLS_EDGE.id] = true;
        VALID_BIOMES[EnumBiome.JUNGLE.id] = true;
        VALID_BIOMES[EnumBiome.JUNGLE_HILLS.id] = true;
        VALID_BIOMES[EnumBiome.JUNGLE_EDGE.id] = true;
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
        VALID_BIOMES[EnumBiome.SUNFLOWER_PLAINS.id] = true;
        VALID_BIOMES[EnumBiome.DESERT_M.id] = true;
        VALID_BIOMES[EnumBiome.EXTREME_HILLS_M.id] = true;
        VALID_BIOMES[EnumBiome.FLOWER_FOREST.id] = true;
        VALID_BIOMES[EnumBiome.TAIGA_M.id] = true;
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

    public static void init() {
        final NukkitRandom rand = new NukkitRandom(Server.getInstance().getDefaultLevel().getSeed());
        double angle = rand.nextDouble() * 6.283185307179586;
        int spread = SPREAD;

        int spreadCount = 0;
        int nextCount = 0;

        for (int i = 0; i < COUNT; ++i) {
            final double radius = (double) (4 * DISTANCE + DISTANCE * nextCount * 6) + (rand.nextDouble() - 0.5) * (double) DISTANCE * 2.5;
            final int cx = (int) Math.round(Math.cos(angle) * radius);
            final int cz = (int) Math.round(Math.sin(angle) * radius);

            strongholdPos[i] = Level.chunkHash(cx, cz);

            angle += 6.283185307179586 / (double) spread;
            ++spreadCount;
            if (spreadCount == spread) {
                ++nextCount;
                spreadCount = 0;
                spread += 2 * spread / (nextCount + 1);
                spread = Math.min(spread, COUNT - i);
                angle += rand.nextDouble() * 6.283185307179586;
            }
        }
    }

    @Override
    public void populate(final ChunkManager level, final int chunkX, final int chunkZ, final NukkitRandom random, final FullChunk chunk) {
        //\\ StrongholdFeature::isFeatureChunk(BiomeSource const &,Random &,ChunkPos const &,uint)
        if (VALID_BIOMES[chunk.getBiomeId(7, 7)]) {
            final long hash = Level.chunkHash(chunkX, chunkZ);
            for (final long chunkPos : strongholdPos) {
                if (hash == chunkPos) {
                    //\\ StrongholdFeature::createStructureStart(Dimension &,BiomeSource &,Random &,ChunkPos const &)
                    final StrongholdStart start = new StrongholdStart(level, chunkX, chunkZ);
                    start.generatePieces(level, chunkX, chunkZ);

                    if (start.isValid()) { //TODO: serialize nbt
                        final long seed = level.getSeed();
                        random.setSeed(seed);
                        final int r1 = random.nextInt();
                        final int r2 = random.nextInt();

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
                                    Server.getInstance().getScheduler().scheduleAsyncTask(new CallbackableChunkGenerationTask<>(chunk.getProvider().getLevel(), ck, start, structure -> structure.postProcess(level, rand, new BoundingBox(x, z, x + 15, z + 15), f_cx, f_cz)));
                                }
                            }
                        }
                    }

                    break;
                }
            }
        }
    }

    public static class StrongholdStart extends StructureStart {
        public StrongholdStart(final ChunkManager level, final int chunkX, final int chunkZ) {
            super(level, chunkX, chunkZ);
        }

        @Override
        public void generatePieces(final ChunkManager level, final int chunkX, final int chunkZ) {
            synchronized (StrongholdPieces.getLock()) {
                int count = 0;
                final long seed = level.getSeed();
                StrongholdPieces.StartPiece start;

                do {
                    pieces.clear();
                    boundingBox = BoundingBox.getUnknownBox();
                    random.setSeed(seed + count++);
                    random.setSeed((long) chunkX * random.nextInt() ^ (long) chunkZ * random.nextInt() ^ level.getSeed());
                    StrongholdPieces.resetPieces();

                    start = new StrongholdPieces.StartPiece(random, (chunkX << 4) + 2, (chunkZ << 4) + 2);
                    pieces.add(start);
                    start.addChildren(start, pieces, random);

                    final List<StructurePiece> children = start.pendingChildren;
                    while (!children.isEmpty()) {
                        children.remove(random.nextBoundedInt(children.size())).addChildren(start, pieces, random);
                    }

                    calculateBoundingBox();
                    moveBelowSeaLevel(64, random, 10);
                } while (pieces.isEmpty() || start.portalRoomPiece == null);
            }
        }

        @Override //\\ StrongholdStart::getType(void) // 5
        public String getType() {
            return "Stronghold";
        }
    }
}
