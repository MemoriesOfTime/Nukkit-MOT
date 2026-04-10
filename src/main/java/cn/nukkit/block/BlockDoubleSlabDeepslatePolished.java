package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemTool;

public class BlockDoubleSlabDeepslatePolished extends BlockSolidMeta {

    public BlockDoubleSlabDeepslatePolished() {
        this(0);
    }

    public BlockDoubleSlabDeepslatePolished(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return POLISHED_DEEPSLATE_DOUBLE_SLAB;
    }

    @Override
    public double getHardness() {
        return 3.5;
    }

    @Override
    public double getResistance() {
        return 15;
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
    public String getName() {
        return "Cobbled Deepslate Slab";
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(POLISHED_DEEPSLATE_DOUBLE_SLAB));
    }

    @Override
    public Item[] getDrops(Item item) {
        if (item.isPickaxe() && item.getTier() >= ItemTool.TIER_STONE) {
            return new Item[]{
                new ItemBlock(Block.get(POLISHED_DEEPSLATE_SLAB), 0, 2)
            };
        }
        return Item.EMPTY_ARRAY;
    }
}
