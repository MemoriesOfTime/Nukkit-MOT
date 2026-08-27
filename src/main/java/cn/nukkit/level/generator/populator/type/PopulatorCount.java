package cn.nukkit.level.generator.populator.type;

import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.NukkitRandom;

/**
 * @author DaPorkchop_
 *
 * A populator that generates an object a certain amount of times.
 * This prevents the exact same code from being repeated in nearly every single populator
 */
public abstract class PopulatorCount extends Populator {

    private int randomAmount;
    private int baseAmount;

    public final void setRandomAmount(int randomAmount) {
        this.randomAmount = randomAmount + 1;
    }

    public final void setBaseAmount(int baseAmount) {
        this.baseAmount = baseAmount;
    }

    /**
     * How many objects this populator always places in a chunk.
     *
     * <p>Without a getter the amount can only ever be overwritten, never adjusted: a server that
     * wants half the trees of a biome has no way to learn how many that biome asked for.
     */
    public final int getBaseAmount() {
        return this.baseAmount;
    }

    /**
     * The exclusive upper bound of the extra amount rolled on top of {@link #getBaseAmount()}.
     *
     * <p>Note that {@link #setRandomAmount(int)} stores its argument plus one, so this returns
     * the bound handed to the random source rather than the value that was passed in.
     */
    public final int getRandomAmount() {
        return this.randomAmount;
    }

    @Override
    public final void populate(ChunkManager level, int chunkX, int chunkZ, NukkitRandom random, FullChunk chunk) {
        int count = baseAmount + random.nextBoundedInt(randomAmount);
        for (int i = 0; i < count; i++) {
            populateCount(level, chunkX, chunkZ, random, chunk);
        }
    }

    protected abstract void populateCount(ChunkManager level, int chunkX, int chunkZ, NukkitRandom random, FullChunk chunk);
}
