package cn.nukkit.block;

import cn.nukkit.item.ItemTool;

public class BlockStairsMudBrick extends BlockStairs {
    public BlockStairsMudBrick() {
        this(0);
    }

    public BlockStairsMudBrick(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Mud Brick Stairs";
    }

    @Override
    public int getId() {
        return MUD_BRICK_STAIRS;
    }

    @Override
    public double getResistance() {
        return 3;
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
}
