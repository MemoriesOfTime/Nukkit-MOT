package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockFencePaleOak extends BlockFence {

    public BlockFencePaleOak() {
    }

    @Override
    public int getId() {
        return PALE_OAK_FENCE;
    }

    @Override
    public String getName() {
        return "Pale Oak Fence";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.QUARTZ_BLOCK_COLOR;
    }
}
