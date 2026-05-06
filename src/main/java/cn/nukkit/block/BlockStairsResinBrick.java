package cn.nukkit.block;

import cn.nukkit.item.ItemTool;
import cn.nukkit.utils.BlockColor;

public class BlockStairsResinBrick extends BlockStairs {
    public BlockStairsResinBrick() {
        this(0);
    }

    public BlockStairsResinBrick(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Resin Brick Stairs";
    }

    @Override
    public int getId() {
        return RESIN_BRICK_STAIRS;
    }

    @Override
    public double getResistance() {
        return 6;
    }

    @Override
    public double getHardness() {
        return 1.5;
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
        return BlockColor.ORANGE_TERRACOTA_BLOCK_COLOR;
    }
}
