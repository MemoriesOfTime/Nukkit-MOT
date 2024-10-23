package cn.nukkit.block;

import cn.nukkit.item.ItemTool;

public class BlockStairsPolishedDeepslate extends BlockStairs {
    public BlockStairsPolishedDeepslate() {
        this(0);
    }

    public BlockStairsPolishedDeepslate(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return POLISHED_DEEPSLATE_STAIRS;
    }

    @Override
    public String getName() {
        return "Polished Deepslate Stairs";
    }

    @Override
    public double getHardness() {
        return 3.5;
    }

    @Override
    public double getResistance() {
        return 6;
    }

    @Override
    public boolean canHarvestWithHand() {
        return false;
    }

    @Override
    public int getToolTier() {
        return ItemTool.TIER_WOODEN;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_PICKAXE;
    }
}
