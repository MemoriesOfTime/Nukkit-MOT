package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemTool;

public class BlockDoubleSlabBamboo extends BlockSolidMeta {

    public BlockDoubleSlabBamboo() {
        this(0);
    }

    public BlockDoubleSlabBamboo(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return BAMBOO_DOUBLE_SLAB;
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
        return ItemTool.TYPE_AXE;
    }

    @Override
    public String getName() {
        return "Bamboo Slab";
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(BAMBOO_SLAB));
    }

    @Override
    public Item[] getDrops(Item item) {
        return new Item[]{
                Item.get(Item.BAMBOO_SLAB, 0, 2)
        };
    }
}
