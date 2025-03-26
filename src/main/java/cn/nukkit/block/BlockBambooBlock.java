package cn.nukkit.block;

import cn.nukkit.item.ItemTool;

public class BlockBambooBlock extends BlockWood {
    public BlockBambooBlock() {
        this(0);
    }

    public BlockBambooBlock(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return BAMBOO_BLOCK;
    }

    @Override
    public String getName() {
        return "Bamboo Block";
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
    public int getBurnAbility() {
        return 20;
    }

    @Override
    public int getBurnChance() {
        return 5;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_AXE;
    }

    @Override
    protected int getStrippedId() {
        return STRIPPED_BAMBOO_BLOCK;
    }
}
