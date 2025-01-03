package cn.nukkit.level.generator.populator.type;

import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.generator.OldNormal;
import cn.nukkit.level.generator.populator.helper.PopulatorHelpers;

public abstract class PopulatorOceanFloorSurfaceBlock extends PopulatorSurfaceBlock {

    @Override
    protected int getHighestWorkableBlock(ChunkManager level, int x, int z, FullChunk chunk) {
        int y;
        for (y = OldNormal.seaHeight - 1; y >= 0; --y) {
            if (!PopulatorHelpers.isNonOceanSolid(chunk.getBlockId(x, y, z))) {
                break;
            }
        }
        return y == 0 ? -1 : ++y;
    }
}
