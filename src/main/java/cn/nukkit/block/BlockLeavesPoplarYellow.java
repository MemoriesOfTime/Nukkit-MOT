package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockLeavesPoplarYellow extends BlockLeavesPoplar {

    public BlockLeavesPoplarYellow() {
        this(0);
    }

    public BlockLeavesPoplarYellow(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return YELLOW_POPLAR_LEAVES;
    }

    @Override
    public String getName() {
        return "Yellow Poplar Leaves";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.YELLOW_BLOCK_COLOR;
    }
}
