package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemTool;
import cn.nukkit.utils.BlockColor;

public class BlockDoubleSlabPoplar extends BlockSolidMeta {

    public BlockDoubleSlabPoplar() {
        this(0);
    }

    public BlockDoubleSlabPoplar(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return POPLAR_DOUBLE_SLAB;
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
        return "Poplar Slab";
    }

    @Override
    public Item toItem() {
        return Item.fromString("minecraft:poplar_slab");
    }

    @Override
    public Item[] getDrops(Item item) {
        Item slab = toItem();
        slab.setCount(2);
        return new Item[]{slab};
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.SPRUCE_BLOCK_COLOR;
    }
}
