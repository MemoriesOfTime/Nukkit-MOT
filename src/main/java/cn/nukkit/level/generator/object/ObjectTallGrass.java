package cn.nukkit.level.generator.object;

import cn.nukkit.block.Block;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.math.NukkitRandom;
import cn.nukkit.math.Vector3;
import cn.nukkit.utils.Utils;

import java.util.concurrent.ThreadLocalRandom;

/**
 * @author ItsLucas
 * Nukkit Project
 */

public class ObjectTallGrass {

    public static void growGrass(ChunkManager level, Vector3 pos, NukkitRandom random) {
        for (int i = 0; i < 128; ++i) {
            int num = 0;

            int x = pos.getFloorX();
            int y = pos.getFloorY() + 1;
            int z = pos.getFloorZ();

            while (true) {
                if (num >= i >> 4) {
                    if (level.getBlockIdAt(x, y, z) == Block.AIR) {
                        if (random.nextBoundedInt(8) == 0) {
                            if (random.nextBoolean()) {
                                level.setBlockAt(x, y, z, Block.DANDELION);
                            } else {
                                level.setBlockAt(x, y, z, Block.POPPY);
                            }
                        } else {
                            level.setBlockAt(x, y, z, Block.TALL_GRASS, 1);
                        }
                    }

                    break;
                }

                x += random.nextRange(-1, 1);
                y += random.nextRange(-1, 1) * random.nextBoundedInt(3) >> 1;
                z += random.nextRange(-1, 1);

                if (level.getBlockIdAt(x, y - 1, z) != Block.GRASS || y > 255 || y < 0) {
                    break;
                }

                ++num;
            }
        }
    }

    public static void growSeagrass(ChunkManager level, Vector3 pos) {
        int maxBlockY = level.getMaxBlockY();
        int minBlockY = level.getMinBlockY();

        for (int i = 0; i < 48; ++i) {
            int num = 0;

            int x = pos.getFloorX();
            int y = pos.getFloorY() + 1;
            int z = pos.getFloorZ();

            while (true) {
                if (num >= i >> 4) {
                    int block = level.getBlockIdAt(x, y, z);
                    if (block == Block.WATER || block == Block.STILL_WATER) {
                        //if (ThreadLocalRandom.current().nextInt(8) == 0) {
                        // TODO: coral & tall seagrass
                        //} else {
                        level.setBlockAt(x, y, z, Block.SEAGRASS, 0);
                        level.setBlockAtLayer(x, y, z, 1, Block.WATER, 0);
                        //}
                    }

                    break;
                }

                x += Utils.rand(-1, 1);
                y += Utils.rand(-1, 1) * ThreadLocalRandom.current().nextInt(3) >> 1;
                z += Utils.rand(-1, 1);

                int block;
                if (y > maxBlockY || y < minBlockY || ((block = level.getBlockIdAt(x, y - 1, z)) != Block.DIRT && block != Block.SAND && block != Block.GRAVEL)) {
                    break;
                }

                ++num;
            }
        }
    }
}
