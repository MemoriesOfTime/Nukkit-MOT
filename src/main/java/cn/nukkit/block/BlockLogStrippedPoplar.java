package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockLogStrippedPoplar extends BlockWoodStripped {

    public BlockLogStrippedPoplar() {
        super(0);
    }

    public BlockLogStrippedPoplar(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return STRIPPED_POPLAR_LOG;
    }

    @Override
    public String getName() {
        return "Stripped Poplar Log";
    }

    @Override
    public double getResistance() {
        return 2;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.SPRUCE_BLOCK_COLOR;
    }
}
