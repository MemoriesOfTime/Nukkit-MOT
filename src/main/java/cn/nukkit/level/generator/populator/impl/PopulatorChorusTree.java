package cn.nukkit.level.generator.populator.impl;

import cn.nukkit.block.Block;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.generator.End;
import cn.nukkit.level.generator.object.end.ObjectChorusTree;
import cn.nukkit.level.generator.populator.type.Populator;
import cn.nukkit.math.NukkitRandom;
import cn.nukkit.math.Vector3;

/**
 * @author GoodLucky777
 */
public class PopulatorChorusTree extends Populator {
    private final End theEnd;
    private final ObjectChorusTree objectChorusTree;

    public PopulatorChorusTree(End theEnd) {
        this.theEnd = theEnd;
        this.objectChorusTree = new ObjectChorusTree();
    }

    @Override
    public void populate(ChunkManager level, int chunkX, int chunkZ, NukkitRandom random, FullChunk chunk) {
        if ((long) chunkX * (long) chunkX + (long) chunkZ * (long) chunkZ <= 4096L) {
            return;
        }

        if (theEnd.getIslandHeight(chunkX, chunkZ, 1, 1) > 40f) {
            for (int i = 0; i < random.nextBoundedInt(5); i++) {
                int x = (chunkX << 4) + random.nextBoundedInt(16);
                int z = (chunkZ << 4) + random.nextBoundedInt(16);
                int y = this.getHighestWorkableBlock(level, x, z, chunk);
                if (y > 0) {
                    if (level.getBlockIdAt(x, y + 1, z) == Block.AIR && level.getBlockIdAt(x, y, z) == Block.END_STONE) {
                        objectChorusTree.generate(level, random, new Vector3(x, y + 1, z), 8);
                    }
                }
            }
        }
    }
}
