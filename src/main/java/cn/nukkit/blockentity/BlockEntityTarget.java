package cn.nukkit.blockentity;

import cn.nukkit.block.BlockID;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.nbt.tag.CompoundTag;

public class BlockEntityTarget extends BlockEntity {

    public BlockEntityTarget(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public boolean isBlockEntityValid() {
        return getLevelBlock().getId() == BlockID.TARGET;
    }

    public int getActivePower() {
        return NukkitMath.clamp(namedTag.getInt("activePower"), 0, 15);
    }

    public void setActivePower(int power) {
        namedTag.putInt("activePower", power);
    }
}