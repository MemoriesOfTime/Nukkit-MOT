package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemTool;

public class BlockDoubleSlabCherry extends BlockSolidMeta {

    public BlockDoubleSlabCherry() {
        this(0);
    }

    public BlockDoubleSlabCherry(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return CHERRY_DOUBLE_SLAB;
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
        return "Cherry Slab";
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(CHERRY_SLAB));
    }

    @Override
    public Item[] getDrops(Item item) {
        if (item.isPickaxe()) {
            Item slab = toItem();
            slab.setCount(2);
            return new Item[]{ slab };
        } else {
            return Item.EMPTY_ARRAY;
        }
    }
}
