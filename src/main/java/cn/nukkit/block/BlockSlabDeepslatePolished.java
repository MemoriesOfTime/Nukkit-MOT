package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemTool;
import cn.nukkit.utils.BlockColor;

public class BlockSlabDeepslatePolished extends BlockSlab {
    public BlockSlabDeepslatePolished() {
        this(0);
    }

    public BlockSlabDeepslatePolished(int meta) {
        super(meta, POLISHED_DEEPSLATE_DOUBLE_SLAB);
    }

    @Override
    public int getId() {
        return POLISHED_DEEPSLATE_SLAB;
    }

    @Override
    public String getName() {
        return "Polished Deepslate Slab";
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
    public double getHardness() {
        return 3.5;
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
        return BlockColor.DEEPSLATE_BLOCK_COLOR;
    }
}
