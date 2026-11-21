package cn.nukkit.block;

import cn.nukkit.item.ItemTool;
import cn.nukkit.utils.BlockColor;
import cn.nukkit.utils.DyeColor;

public abstract class BlockSlabWool extends BlockSlab {

    protected BlockSlabWool(int meta, int doubleSlab) {
        super(meta, doubleSlab);
    }

    public abstract DyeColor getDyeColor();

    @Override
    public String getName() {
        return getDyeColor().getName() + " Wool Slab";
    }

    @Override
    public double getHardness() {
        return 0.8;
    }

    @Override
    public double getResistance() {
        return 0.8;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_SHEARS;
    }

    @Override
    public int getBurnChance() {
        return 30;
    }

    @Override
    public int getBurnAbility() {
        return 60;
    }

    @Override
    public BlockColor getColor() {
        return getDyeColor().getColor();
    }
}
