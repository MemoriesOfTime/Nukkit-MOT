package cn.nukkit.block;

import cn.nukkit.item.ItemTool;
import cn.nukkit.utils.BlockColor;

public class BlockWallSulfur extends BlockWallIndependentID {
    public BlockWallSulfur() {
        this(0);
    }

    public BlockWallSulfur(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Sulfur Wall";
    }

    @Override
    public int getId() {
        return SULFUR_WALL;
    }

    @Override
    public String getIdentifier() {
        return "minecraft:sulfur_wall";
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
        return BlockColor.YELLOW_BLOCK_COLOR;
    }
}
