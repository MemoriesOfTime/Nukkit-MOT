package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemTool;
import cn.nukkit.utils.BlockColor;

public class BlockSlabBlackstone extends BlockSlab {
    public BlockSlabBlackstone() {
        this(0);
    }

    public BlockSlabBlackstone(int meta) {
        super(meta, BLACKSTONE_DOUBLE_SLAB);
    }

    @Override
    public int getId() {
        return BLACKSTONE_SLAB;
    }

    @Override
    public String getName() {
        return "Blackstone Slab";
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_PICKAXE;
    }

    @Override
    public double getResistance() {
        return 6;
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
        return BlockColor.BLACK_BLOCK_COLOR;
    }
}
