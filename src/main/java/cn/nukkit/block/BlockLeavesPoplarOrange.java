package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockLeavesPoplarOrange extends BlockLeavesPoplar {

    public BlockLeavesPoplarOrange() {
        this(0);
    }

    public BlockLeavesPoplarOrange(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return ORANGE_POPLAR_LEAVES;
    }

    @Override
    public String getName() {
        return "Orange Poplar Leaves";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.ORANGE_BLOCK_COLOR;
    }
}
