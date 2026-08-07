package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemTool;
import cn.nukkit.utils.BlockColor;

public class BlockSlabCinnabar extends BlockSlab {
    public BlockSlabCinnabar() {
        this(0);
    }

    public BlockSlabCinnabar(int meta) {
        super(meta, CINNABAR_DOUBLE_SLAB);
    }

    @Override
    public int getId() {
        return CINNABAR_SLAB;
    }

    @Override
    public String getName() {
        return "Cinnabar Slab";
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
        return 30;
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
        return BlockColor.RED_BLOCK_COLOR;
    }
}
