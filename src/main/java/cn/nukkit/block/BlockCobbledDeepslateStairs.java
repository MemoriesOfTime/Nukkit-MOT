package cn.nukkit.block;

import cn.nukkit.item.ItemTool;

public class BlockCobbledDeepslateStairs extends BlockStairs {
    public BlockCobbledDeepslateStairs() {
        this(0);
    }

    public BlockCobbledDeepslateStairs(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Cobbled Deepslate Stairs";
    }

    @Override
    public int getId() {
        return COBBLED_DEEPSLATE_STAIRS;
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
