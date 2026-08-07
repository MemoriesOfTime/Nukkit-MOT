package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemTool;
import cn.nukkit.utils.BlockColor;

public class BlockSlabTuffBrick extends BlockSlab {
    public BlockSlabTuffBrick() {
        this(0);
    }

    public BlockSlabTuffBrick(int meta) {
        super(meta, TUFF_BRICK_DOUBLE_SLAB);
    }

    @Override
    public int getId() {
        return TUFF_BRICK_SLAB;
    }

    @Override
    public String getName() {
        return "Tuff Brick Slab";
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
    public double getResistance() {
        return 6;
    }

    @Override
    public double getHardness() {
        return 1.5;
    }

    @Override
    public Item[] getDrops(Item item) {
        if (item.isPickaxe() && item.getTier() >= ItemTool.TIER_WOODEN) {
            return new Item[]{
                    toItem()
            };
        } else {
            return Item.EMPTY_ARRAY;
        }
    }

    @Override
    public boolean canHarvestWithHand() {
        return false;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.TUFF_BLOCK_COLOR;
    }
}
