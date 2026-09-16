package cn.nukkit.blockentity;

import cn.nukkit.Player;
import java.util.HashSet;
import java.util.Set;

import cn.nukkit.block.Block;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;

public class BlockEntityEnderChest extends BlockEntitySpawnable {

    /**
     * Players looking into this ender chest right now. Kept on the block entity because a
     * {@link cn.nukkit.block.BlockEnderChest} object is created anew for every lookup: a set on the
     * block saw one viewer at a time, and the first player to close the window shut the lid for
     * everyone else. Not saved: a window never survives a restart.
     */
    private final Set<Player> viewers = new HashSet<>();

    public BlockEntityEnderChest(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    public Set<Player> getViewers() {
        return viewers;
    }

    @Override
    public boolean isBlockEntityValid() {
        return this.getBlock().getId() == Block.ENDER_CHEST;
    }

    @Override
    public String getName() {
        return "EnderChest";
    }

    @Override
    public CompoundTag getSpawnCompound() {
        return new CompoundTag()
                .putString("id", BlockEntity.ENDER_CHEST)
                .putInt("x", (int) this.x)
                .putInt("y", (int) this.y)
                .putInt("z", (int) this.z);
    }
}
