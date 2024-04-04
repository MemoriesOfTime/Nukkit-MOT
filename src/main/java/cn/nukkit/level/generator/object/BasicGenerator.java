package cn.nukkit.level.generator.object;

import cn.nukkit.block.Block;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.NukkitRandom;
import cn.nukkit.math.Vector3;

public abstract class BasicGenerator {

    public abstract boolean generate(ChunkManager level, NukkitRandom rand, Vector3 position);

    protected void setBlockAndNotifyAdequately(ChunkManager level, BlockVector3 pos, Block state) {
        setBlock(level, new Vector3(pos.x, pos.y, pos.z), state);
    }

    protected void setBlockAndNotifyAdequately(ChunkManager level, Vector3 pos, Block state) {
        setBlock(level, pos, state);
    }

    protected void setBlockAndNotifyAdequately(ChunkManager level, int x, int y, int z, Block state) {
        level.setBlockAt(x, y, z, state.getId(), state.getDamage());
    }

    protected void setBlock(ChunkManager level, Vector3 v, Block b) {
        level.setBlockAt((int) v.x, (int) v.y, (int) v.z, b.getId(), b.getDamage());
    }
}