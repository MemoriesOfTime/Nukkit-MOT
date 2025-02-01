package cn.nukkit.level.generator.ground;

import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.math.NukkitRandom;

public class GroundGeneratorPatchStone extends GroundGenerator {

    @Override
    public void generateTerrainColumn(ChunkManager world, BaseFullChunk chunkData, NukkitRandom random, int chunkX, int chunkZ, int biome, double surfaceNoise) {
        if (surfaceNoise > 1.0D) {
            setTopMaterial(STONE);
            setGroundMaterial(STONE);
        } else {
            setTopMaterial(GRASS);
            setGroundMaterial(DIRT);
        }
        super.generateTerrainColumn(world, chunkData, random, chunkX, chunkZ, biome, surfaceNoise);
    }
}
