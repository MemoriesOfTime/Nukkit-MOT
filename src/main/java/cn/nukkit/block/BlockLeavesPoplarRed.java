package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockLeavesPoplarRed extends BlockLeavesPoplar {

    public BlockLeavesPoplarRed() {
        this(0);
    }

    public BlockLeavesPoplarRed(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return RED_POPLAR_LEAVES;
    }

    @Override
    public String getName() {
        return "Red Poplar Leaves";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.RED_BLOCK_COLOR;
    }
}
