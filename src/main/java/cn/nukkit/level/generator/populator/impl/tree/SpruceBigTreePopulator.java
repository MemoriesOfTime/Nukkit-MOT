package cn.nukkit.level.generator.populator.impl.tree;

import cn.nukkit.block.Block;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.generator.object.tree.ObjectBigSpruceTree;
import cn.nukkit.level.generator.populator.type.Populator;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.math.NukkitRandom;

public class SpruceBigTreePopulator extends Populator {
    private ChunkManager level;
    private int randomAmount;
    private int baseAmount;

    public SpruceBigTreePopulator() {
    }

    public void setRandomAmount(int randomAmount) {
        this.randomAmount = randomAmount;
    }

    public void setBaseAmount(int baseAmount) {
        this.baseAmount = baseAmount;
    }

    @Override
    public void populate(ChunkManager level, int chunkX, int chunkZ, NukkitRandom random, FullChunk chunk) {
        this.level = level;
        int amount = random.nextBoundedInt(this.randomAmount + 1) + this.baseAmount;

        for (int i = 0; i < amount; ++i) {

            int x = NukkitMath.randomRange(random, (chunkX << 4) + 1, (chunkX << 4) + 13);
            int z = NukkitMath.randomRange(random, (chunkZ << 4) + 1, (chunkZ << 4) + 13);
            int y = this.getHighestWorkableBlock(x, z);

            if (y == -1) {
                continue;
            }


            if (!canPlaceTree(level, x, y, z)) {
                continue;
            }


            generateBigSpruceTree(level, x, y, z, random);
        }
    }

    private boolean canPlaceTree(ChunkManager level, int x, int y, int z) {

        for (int xx = 0; xx < 2; xx++) {
            for (int zz = 0; zz < 2; zz++) {
                int blockId = level.getBlockIdAt(x + xx, y - 1, z + zz);
                if (blockId != Block.GRASS && blockId != Block.DIRT) {
                    return false;
                }


                for (int yy = y; yy < y; yy++) {
                    if (!canReplace(level.getBlockIdAt(x + xx, yy, z + zz))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean canReplace(int blockId) {
        return blockId == Block.AIR || blockId == Block.SAPLING || blockId == Block.SNOW_LAYER;
    }

    private void generateBigSpruceTree(ChunkManager level, int x, int y, int z, NukkitRandom random) {

        for (int xx = 0; xx < 2; xx++) {
            for (int zz = 0; zz < 2; zz++) {
                level.setBlockAt(x + xx, y, z + zz, Block.SAPLING);
            }
        }


        new ObjectBigSpruceTree(0.45f, 3, false).placeObject(level, x, y, z, random);


        for (int xx = 0; xx < 2; xx++) {
            for (int zz = 0; zz < 2; zz++) {
                if (level.getBlockIdAt(x + xx, y, z + zz) == Block.SAPLING) {
                    level.setBlockAt(x + xx, y, z + zz, Block.AIR);
                }
            }
        }
    }

    private int getHighestWorkableBlock(int x, int z) {
        int y;
        for (y = 255; y > 0; --y) {
            int b = this.level.getBlockIdAt(x, y, z);
            if (b == Block.DIRT || b == Block.GRASS) {
                break;
            } else if (b != Block.AIR && b != Block.SNOW_LAYER) {
                return -1;
            }
        }
        return ++y;
    }
}