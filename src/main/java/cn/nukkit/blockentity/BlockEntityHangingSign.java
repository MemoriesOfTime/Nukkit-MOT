package cn.nukkit.blockentity;

import cn.nukkit.block.Block;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;

public class BlockEntityHangingSign extends BlockEntitySign {

    public BlockEntityHangingSign(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public boolean isBlockEntityValid() {
        Block block = getBlock();
        return block instanceof cn.nukkit.block.BlockHangingSign;
    }

    @Override
    public CompoundTag getSpawnCompound() {
        CompoundTag tag = super.getSpawnCompound();
        tag.putString("id", BlockEntity.HANGING_SIGN);
        return tag;
    }

    @Override
    public CompoundTag getSpawnCompound(int protocol) {
        CompoundTag tag = super.getSpawnCompound(protocol);
        tag.putString("id", BlockEntity.HANGING_SIGN);
        return tag;
    }
}
