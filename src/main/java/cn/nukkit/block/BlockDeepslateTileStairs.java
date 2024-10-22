package cn.nukkit.block;

import cn.nukkit.item.ItemTool;

public class BlockDeepslateTileStairs extends BlockStairs {
    public BlockDeepslateTileStairs() {
        this(0);
    }

    public BlockDeepslateTileStairs(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return DEEPSLATE_TILE_STAIRS;
    }

    @Override
    public String getName() {
        return "Deepslate Tile Stairs";
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
