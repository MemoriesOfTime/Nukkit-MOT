package cn.nukkit.level.generator.ground;

import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.math.NukkitRandom;
import cn.nukkit.block.Block;

public class GroundGeneratorSandOcean extends GroundGenerator {

    public GroundGeneratorSandOcean() {
        setTopMaterial(SAND);
        setGroundMaterial(SANDSTONE);
    }

    @Override
    public void generateTerrainColumn(ChunkManager world, BaseFullChunk chunkData, NukkitRandom random, int chunkX, int chunkZ, int biome, double surfaceNoise) {
        int seaLevel = 64;

        int topMat = this.topMaterial;
        int groundMat = this.groundMaterial;

        int x = chunkX & 0xF;
        int z = chunkZ & 0xF;

        int surfaceHeight = Math.max((int) (surfaceNoise / 3.0D + 3.0D + random.nextDouble() * 0.25D), 1);
        int deep = -1;

        for (int y = 255; y >= -64; y--) {
            int mat = chunkData.getBlockId(x, y, z);

            // Генерация бедрока с вариациями
            if (y <= -64 + random.nextBoundedInt(5)) {
                chunkData.setBlock(x, y, z, BEDROCK);
            }
            // Замена всех нижних блоков на deepslate
            else if (mat == Block.AIR || mat == Block.STONE || y <= seaLevel - 1) {
                chunkData.setBlock(x, y, z, DEEPSLATE);
            }
            else {
                if (mat == AIR) {
                    deep = -1;
                } else if (mat == STONE) {
                    if (deep == -1) {
                        if (y >= seaLevel - 5 && y <= seaLevel) {
                            topMat = this.topMaterial;
                            groundMat = this.groundMaterial;
                        }

                        deep = surfaceHeight;
                        if (y >= seaLevel - 2) {
                            chunkData.setBlock(x, y, z, topMat, this.topData);
                        } else if (y < seaLevel - 8 - surfaceHeight) {
                            topMat = AIR;
                            groundMat = STONE;
                            chunkData.setBlock(x, y, z, SAND);
                        } else {
                            chunkData.setBlock(x, y, z, groundMat, this.groundData);
                        }
                    } else if (deep > 0) {
                        deep--;
                        chunkData.setBlock(x, y, z, groundMat, this.groundData);

                        if (deep == 0 && groundMat == SAND) {
                            deep = random.nextBoundedInt(4) + Math.max(0, y - seaLevel - 1);
                            groundMat = SANDSTONE;
                        }
                    }
                }
            }
        }
    }
}
