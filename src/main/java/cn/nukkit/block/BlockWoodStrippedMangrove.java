package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockWoodStrippedMangrove extends BlockWoodStripped {

    public BlockWoodStrippedMangrove() {
        super(0);
    }

    public BlockWoodStrippedMangrove(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return STRIPPED_MANGROVE_WOOD;
    }

    @Override
    public String getName() {
        return "Stripped Mangrove Wood";
    }

    @Override
    public int getBurnAbility() {
        return 5;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.RED_BLOCK_COLOR;
    }
}
