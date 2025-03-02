package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemTool;

public class BlockDoubleSlabBambooMosaic extends BlockSolidMeta {

    public BlockDoubleSlabBambooMosaic() {
        this(0);
    }

    public BlockDoubleSlabBambooMosaic(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return BAMBOO_MOSAIC_DOUBLE_SLAB;
    }

    @Override
    public double getHardness() {
        return 2;
    }

    @Override
    public double getResistance() {
        return 15;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_AXE;
    }

    @Override
    public String getName() {
        return "Bamboo Slab";
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(BAMBOO_MOSAIC_SLAB));
    }

    @Override
    public Item[] getDrops(Item item) {
        return new Item[]{
                Item.get(Item.BAMBOO_MOSAIC_SLAB, 0, 2)
        };
    }
}
