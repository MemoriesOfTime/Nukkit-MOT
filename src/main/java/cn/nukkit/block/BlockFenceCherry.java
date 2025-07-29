package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockFenceCherry extends BlockFence {
    public BlockFenceCherry() {
    }

    @Override
    public int getId() {
        return CHERRY_FENCE;
    }

    @Override
    public String getName() {
        return "Cherry Fence";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.WHITE_TERRACOTA_BLOCK_COLOR;
    }
}
