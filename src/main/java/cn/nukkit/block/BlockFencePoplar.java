package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockFencePoplar extends BlockFence {

    public BlockFencePoplar() {
        this(0);
    }

    public BlockFencePoplar(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return POPLAR_FENCE;
    }

    @Override
    public String getName() {
        return "Poplar Fence";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.SPRUCE_BLOCK_COLOR;
    }
}
