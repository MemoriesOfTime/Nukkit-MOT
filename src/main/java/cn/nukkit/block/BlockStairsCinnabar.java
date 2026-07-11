package cn.nukkit.block;

import cn.nukkit.item.ItemTool;
import cn.nukkit.utils.BlockColor;

public class BlockStairsCinnabar extends BlockStairs {
    public BlockStairsCinnabar() {
        this(0);
    }

    public BlockStairsCinnabar(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return CINNABAR_STAIRS;
    }

    @Override
    public String getName() {
        return "Cinnabar Stairs";
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
        return BlockColor.RED_BLOCK_COLOR;
    }
}
