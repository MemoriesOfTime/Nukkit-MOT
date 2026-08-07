package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockStrippedBambooBlock extends BlockWoodStripped {
    public BlockStrippedBambooBlock() {
        super(0);
    }

    public BlockStrippedBambooBlock(int meta) {
        super(meta);
    }


    public int getId() {
        return STRIPPED_BAMBOO_BLOCK;
    }

    public String getName() {
        return "Stripped Bamboo Block";
    }

    @Override
    public double getHardness() {
        return 2;
    }

    @Override
    public double getResistance() {
        return 15;
    }

    @Override
    public int getBurnChance() {
        return 5;
    }

    @Override
    public int getBurnAbility() {
        return 20;
    }

    @Override
    protected int getStrippedId() {
        return STRIPPED_BAMBOO_BLOCK;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.YELLOW_BLOCK_COLOR;
    }
}
