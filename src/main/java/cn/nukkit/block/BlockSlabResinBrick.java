package cn.nukkit.block;

import cn.nukkit.item.ItemTool;

public class BlockSlabResinBrick extends BlockSlab {

    public BlockSlabResinBrick() {
        this(0);
    }

    public BlockSlabResinBrick(int meta) {
        super(meta, RESIN_BRICK_DOUBLE_SLAB);
    }

    protected BlockSlabResinBrick(int meta, int doubleSlab) {
        super(meta, doubleSlab);
    }

    @Override
    public int getId() {
        return RESIN_BRICK_SLAB;
    }

    @Override
    public String getName() {
        return "Resin Brick Slab";
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
    public boolean canHarvestWithHand() {
        return false;
    }
}
