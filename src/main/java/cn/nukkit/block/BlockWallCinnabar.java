package cn.nukkit.block;

import cn.nukkit.item.ItemTool;
import cn.nukkit.utils.BlockColor;

public class BlockWallCinnabar extends BlockWallIndependentID {
    public BlockWallCinnabar() {
        this(0);
    }

    public BlockWallCinnabar(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Cinnabar Wall";
    }

    @Override
    public int getId() {
        return CINNABAR_WALL;
    }

    @Override
    public String getIdentifier() {
        return "minecraft:cinnabar_wall";
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
    public boolean canHarvestWithHand() {
        return false;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_PICKAXE;
    }

    @Override
    public int getToolTier() {
        return ItemTool.TIER_WOODEN;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.RED_BLOCK_COLOR;
    }
}
