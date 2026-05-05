package cn.nukkit.block;

import cn.nukkit.item.ItemTool;
import cn.nukkit.utils.BlockColor;

public class BlockStairsSulfur extends BlockStairs {
    public BlockStairsSulfur() {
        this(0);
    }

    public BlockStairsSulfur(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return SULFUR_STAIRS;
    }

    @Override
    public String getName() {
        return "Sulfur Stairs";
    }

    @Override
    public double getHardness() {
        return 1.5;
    }

    @Override
    public double getResistance() {
        return 30;
    }

    @Override
    public int getToolTier() {
        return ItemTool.TIER_WOODEN;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_PICKAXE;
    }

    @Override
    public boolean canHarvestWithHand() {
        return false;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.YELLOW_BLOCK_COLOR;
    }
}
