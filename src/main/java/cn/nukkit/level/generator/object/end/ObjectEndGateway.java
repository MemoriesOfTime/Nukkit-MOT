package cn.nukkit.level.generator.object.end;

import cn.nukkit.block.Block;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityEndGateway;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.Position;
import cn.nukkit.level.generator.object.BasicGenerator;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.NukkitRandom;
import cn.nukkit.math.Vector3;

/**
 * @author GoodLucky777
 */
public class ObjectEndGateway extends BasicGenerator {

    public boolean generate(ChunkManager level, NukkitRandom rand, Vector3 position) {
        return this.generate(level, rand, position, null);
    }

    public boolean generate(ChunkManager level, NukkitRandom rand, Vector3 position, BlockVector3 exitPortal) {
        for (int x = position.getFloorX() - 1; x <= position.getFloorX() + 1; x++) {
            for (int z = position.getFloorZ() - 1; z <= position.getFloorZ() + 1; z++) {
                for (int y = position.getFloorY() - 2; y <= position.getFloorY() + 2; y++) {
                    boolean flagX = position.getFloorX() == x;
                    boolean flagY = position.getFloorY() == y;
                    boolean flagZ = position.getFloorZ() == z;
                    boolean flagFar = Math.abs(y - position.getFloorY()) == 2;

                    if (flagX && flagY && flagZ) {
                        level.setBlockAt(x, y, z, Block.END_GATEWAY);
                        if (exitPortal != null) {
                            BlockEntity endGateway = BlockEntity.createBlockEntity("EndGateway", level.getChunk(x >> 4, z >> 4), BlockEntity.getDefaultCompound(new Position(x, y, z), "EndGateway"));
                            ((BlockEntityEndGateway) endGateway).setExitPortal(exitPortal);
                            level.getChunk(x >> 4, z >> 4).addBlockEntity(endGateway);
                        }
                    } else if (flagY) {
                        level.setBlockAt(x, y, z, Block.AIR);
                    } else if (flagX && flagZ && flagFar) {
                        level.setBlockAt(x, y, z, Block.BEDROCK);
                    } else if (!flagFar && (flagX || flagZ)) {
                        level.setBlockAt(x, y, z, Block.BEDROCK);
                    } else {
                        level.setBlockAt(x, y, z, Block.AIR);
                    }
                }
            }
        }
        return true;
    }
}
