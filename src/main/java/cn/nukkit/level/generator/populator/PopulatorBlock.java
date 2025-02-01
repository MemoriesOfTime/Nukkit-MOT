package cn.wode490390.nukkit.vanillagenerator.populator;

import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.generator.populator.type.Populator;
import cn.nukkit.math.NukkitRandom;

public abstract class PopulatorBlock extends Populator {

    protected int amount;

    public final void setAmount(int amount) {
        this.amount = amount;
    }

    public abstract void decorate(ChunkManager level, int chunkX, int chunkZ, NukkitRandom random, FullChunk chunk);

    @Override
    public void populate(ChunkManager level, int chunkX, int chunkZ, NukkitRandom random, FullChunk chunk) {
        for (int i = 0; i < amount; i++) {
            decorate(level, chunkX, chunkZ, random, chunk);
        }
    }
}
