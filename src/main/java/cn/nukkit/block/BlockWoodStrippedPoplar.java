package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockWoodStrippedPoplar extends BlockWoodStripped {

    public BlockWoodStrippedPoplar() {
        super(0);
    }

    public BlockWoodStrippedPoplar(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return STRIPPED_POPLAR_WOOD;
    }

    @Override
    public String getName() {
        return "Stripped Poplar Wood";
    }

    @Override
    public int getBurnAbility() {
        return 5;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.SPRUCE_BLOCK_COLOR;
    }
}
