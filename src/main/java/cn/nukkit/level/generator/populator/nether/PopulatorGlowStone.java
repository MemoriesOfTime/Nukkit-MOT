package cn.nukkit.level.generator.populator.nether;

import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.NukkitRandom;
import cn.wode490390.nukkit.vanillagenerator.populator.PopulatorBlock;

public class PopulatorGlowStone extends PopulatorBlock {

    private final boolean variableAmount;
    
    public PopulatorGlowStone() {
        this(false);
    }

    public PopulatorGlowStone(boolean variableAmount) {
        this.variableAmount = variableAmount;
    }

    @Override
    public void decorate(ChunkManager level, int chunkX, int chunkZ, NukkitRandom random, FullChunk source) {
        int amount = this.variableAmount ? 1 + random.nextBoundedInt(1 + random.nextBoundedInt(10)) : 10;
        for (int i = 0; i < amount; i++) {

            int sourceX = (chunkX << 4) + random.nextBoundedInt(16);
            int sourceZ = (chunkZ << 4) + random.nextBoundedInt(16);
            int sourceY = 4 + random.nextBoundedInt(120);

            int block = level.getBlockIdAt(sourceX, sourceY, sourceZ);
            if (block != AIR || level.getBlockIdAt(sourceX, sourceY + 1, sourceZ) != NETHERRACK) {
                continue;
            }
            level.setBlockAt(sourceX, sourceY, sourceZ, GLOWSTONE);

            for (int j = 0; j < 1500; j++) {
                int x = sourceX + random.nextBoundedInt(8) - random.nextBoundedInt(8);
                int z = sourceZ + random.nextBoundedInt(8) - random.nextBoundedInt(8);
                int y = sourceY - random.nextBoundedInt(12);
                block = level.getBlockIdAt(x, y, z);
                if (block != AIR) {
                    continue;
                }
                int glowstoneBlockCount = 0;
                if (level.getBlockIdAt(x + 1, y, z) == GLOWSTONE) {
                    glowstoneBlockCount++;
                }
                if (level.getBlockIdAt(x, y + 1, z) == GLOWSTONE) {
                    glowstoneBlockCount++;
                }
                if (level.getBlockIdAt(x, y, z + 1) == GLOWSTONE) {
                    glowstoneBlockCount++;
                }
                if (level.getBlockIdAt(x - 1, y, z) == GLOWSTONE) {
                    glowstoneBlockCount++;
                }
                if (level.getBlockIdAt(x, y - 1, z) == GLOWSTONE) {
                    glowstoneBlockCount++;
                }
                if (level.getBlockIdAt(x, y, z - 1) == GLOWSTONE) {
                    glowstoneBlockCount++;
                }
                if (glowstoneBlockCount == 1) {
                    level.setBlockAt(x, y, z, GLOWSTONE);
                }
            }
        }
    }
}
