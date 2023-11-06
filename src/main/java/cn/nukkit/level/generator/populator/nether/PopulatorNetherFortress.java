package cn.nukkit.level.generator.populator.nether;

import cn.nukkit.Server;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.generator.math.BoundingBox;
import cn.nukkit.level.generator.populator.type.Populator;
import cn.nukkit.level.generator.structure.NetherBridgePieces;
import cn.nukkit.level.generator.structure.StructurePiece;
import cn.nukkit.level.generator.structure.StructureStart;
import cn.nukkit.level.generator.task.CallbackableChunkGenerationTask;
import cn.nukkit.math.NukkitRandom;

import java.util.List;

public class PopulatorNetherFortress extends Populator {
    @Override
    public void populate(final ChunkManager level, final int chunkX, final int chunkZ, final NukkitRandom random, final FullChunk chunk) {
        final int gx = chunkX >> 4;
        final int gz = chunkZ >> 4;
        final long seed = level.getSeed();
        random.setSeed(gx ^ gz << 4 ^ seed);
        random.nextInt();
        if (random.nextBoundedInt(3) == (0x51d8e999 & 3) // salted
            && chunkX == (gx << 4) + 4 + random.nextBoundedInt(8) && chunkZ == (gz << 4) + 4 + random.nextBoundedInt(8)) {
            random.setSeed(seed);
            final int r1 = random.nextInt();
            final int r2 = random.nextInt();

            final NetherFortressStart start = new NetherFortressStart(level, chunkX, chunkZ);
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
                            Server.getInstance().computeThreadPool.submit(new CallbackableChunkGenerationTask<>(
                                chunk.getProvider().getLevel(), ck, start,
                                structure -> structure.postProcess(level, rand, new BoundingBox(x, z, x + 15, z + 15), f_cx, f_cz))
                            );
                        }
                    }
                }
            }
        }
    }

    public static class NetherFortressStart extends StructureStart {
        public NetherFortressStart(final ChunkManager level, final int chunkX, final int chunkZ) {
            super(level, chunkX, chunkZ);
        }

        @Override
        public void generatePieces(final ChunkManager level, final int chunkX, final int chunkZ) {
            final NetherBridgePieces.StartPiece start = new NetherBridgePieces.StartPiece(random, (chunkX << 4) + 2, (chunkZ << 4) + 2);
            pieces.add(start);
            start.addChildren(start, pieces, random);

            final List<StructurePiece> pendingChildren = start.pendingChildren;
            while (!pendingChildren.isEmpty()) {
                pendingChildren.remove(random.nextBoundedInt(pendingChildren.size())).addChildren(start, pieces, random);
            }

            calculateBoundingBox();
            moveInsideHeights(random, 48, 70);
        }

        @Override
        public String getType() {
            return "Fortress";
        }
    }
}
