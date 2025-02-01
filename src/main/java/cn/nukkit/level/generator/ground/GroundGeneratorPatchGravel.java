package cn.nukkit.level.generator.ground;

import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.math.NukkitRandom;

public class GroundGeneratorPatchGravel extends GroundGenerator {

    @Override
    public void generateTerrainColumn(ChunkManager world, BaseFullChunk chunkData, NukkitRandom random, int chunkX, int chunkZ, int biome, double surfaceNoise) {
        if (surfaceNoise < -1.0D || surfaceNoise > 2.0D) {
            setTopMaterial(GRAVEL);
            setGroundMaterial(GRAVEL);
        } else {
            setTopMaterial(GRASS);
            setGroundMaterial(DIRT);
        }
        super.generateTerrainColumn(world, chunkData, random, chunkX, chunkZ, biome, surfaceNoise);
    }
}
