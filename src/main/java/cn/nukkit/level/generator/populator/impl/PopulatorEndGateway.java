package cn.nukkit.level.generator.populator.impl;

import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.generator.End;
import cn.nukkit.level.generator.object.end.ObjectEndGateway;
import cn.nukkit.level.generator.populator.type.Populator;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.NukkitRandom;
import cn.nukkit.math.Vector3;
import lombok.Getter;

/**
 * @author GoodLucky777
 */
public class PopulatorEndGateway extends Populator {

    private final End theEnd;

    private final ObjectEndGateway objectEndGateway;

    @Getter
    private BlockVector3 exitPortalPosition;

    public PopulatorEndGateway(End theEnd) {
        this.theEnd = theEnd;
        this.objectEndGateway = new ObjectEndGateway();
        this.exitPortalPosition = theEnd.getSpawn().asBlockVector3();
    }

    @Override
    public void populate(ChunkManager level, int chunkX, int chunkZ, NukkitRandom random, FullChunk chunk) {
        if ((long) chunkX * (long) chunkX + (long) chunkZ * (long) chunkZ <= 4096L) {
            return;
        }

        if (theEnd.getIslandHeight(chunkX, chunkZ, 1, 1) > 40f) {
            if (random.nextBoundedInt(700) == 0) {
                int x = (chunkX << 4) + random.nextBoundedInt(16);
                int z = (chunkZ << 4) + random.nextBoundedInt(16);
                int y = this.getHighestWorkableBlock(level, x, z, chunk) + random.nextBoundedInt(7) + 3;

                if (y > 1 && y < 254) {
                    objectEndGateway.generate(level, random, new Vector3(x, y, z), exitPortalPosition);
                }
            }
        }
    }

    public void setExitPortalPosition(BlockVector3 exitPortalPosition) {
        this.exitPortalPosition = exitPortalPosition;
    }
}
