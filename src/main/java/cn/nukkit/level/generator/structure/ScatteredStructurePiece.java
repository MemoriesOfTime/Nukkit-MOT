package cn.nukkit.level.generator.structure;

import cn.nukkit.block.BlockID;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.NukkitRandom;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

public abstract class ScatteredStructurePiece {
    private static final IntList PLANTS = new IntArrayList() {
        {
            add(BlockID.LOG);
            add(BlockID.LEAVES);
            add(BlockID.TALL_GRASS);
            add(BlockID.DEAD_BUSH);
            add(BlockID.DANDELION);
            add(BlockID.RED_FLOWER);
            add(BlockID.BROWN_MUSHROOM);
            add(BlockID.RED_MUSHROOM);
            add(BlockID.CACTUS);
            add(BlockID.SUGARCANE_BLOCK);
            add(BlockID.PUMPKIN);
            add(BlockID.BROWN_MUSHROOM_BLOCK);
            add(BlockID.RED_MUSHROOM_BLOCK);
            add(BlockID.MELON_BLOCK);
            add(BlockID.VINE);
            add(BlockID.WATER_LILY);
            add(BlockID.COCOA);
            add(BlockID.LEAVES2);
            add(BlockID.LOG2);
            add(BlockID.DOUBLE_PLANT);
        }
    };

    protected final StructureBoundingBox boundingBox;
    private int horizPos = -1;

    public ScatteredStructurePiece(final BlockVector3 pos, final BlockVector3 size) {
        boundingBox = new StructureBoundingBox(new BlockVector3(pos.x, pos.y, pos.z), new BlockVector3(pos.x + size.x - 1, pos.y + size.y - 1, pos.z + size.z - 1));
    }

    public StructureBoundingBox getBoundingBox() {
        return boundingBox;
    }

    protected void adjustHorizPos(final ChunkManager level) {
        if (horizPos >= 0) {
            return;
        }

        int sumY = 0;
        int blockCount = 0;
        for (int x = boundingBox.getMin().x; x <= boundingBox.getMax().x; x++) {
            for (int z = boundingBox.getMin().z; z <= boundingBox.getMax().z; z++) {
                int y = level.getChunk(x >> 4, z >> 4).getHighestBlockAt(x & 0xf, z & 0xf);
                int id = level.getBlockIdAt(x, y - 1, z);
                while (PLANTS.contains(id) && y > 1) {
                    y--;
                    id = level.getBlockIdAt(x, y - 1, z);
                }
                sumY += Math.max(64, y + 1);
                blockCount++;
            }
        }

        horizPos = sumY / blockCount;
        boundingBox.offset(new BlockVector3(0, horizPos - boundingBox.getMin().y, 0));
    }

    public abstract void generate(ChunkManager world, NukkitRandom random);
}
