package cn.nukkit.block;

import cn.nukkit.item.ItemTool;

public class BlockSlabMudBrick extends BlockSlab {

    public BlockSlabMudBrick() {
        this(0);
    }

    public BlockSlabMudBrick(int meta) {
        super(meta, MUD_BRICK_DOUBLE_SLAB);
    }

    protected BlockSlabMudBrick(int meta, int doubleSlab) {
        super(meta, doubleSlab);
    }

    @Override
    public int getId() {
        return MUD_BRICK_SLAB;
    }

    @Override
    public String getName() {
        return "Mud Brick Slab";
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_PICKAXE;
    }

    @Override
    public int getToolTier() {
        return ItemTool.TIER_WOODEN;
    }
}
