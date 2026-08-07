package cn.nukkit.blockentity;

import cn.nukkit.block.BlockID;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;

public class BlockEntityPotentSulfur extends BlockEntity {

    public BlockEntityPotentSulfur(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public boolean isBlockEntityValid() {
        return getBlock().getId() == BlockID.POTENT_SULFUR;
    }
}
