package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemTool;
import cn.nukkit.utils.BlockColor;

public class BlockDoubleSlabMangrove extends BlockSolidMeta {

    public BlockDoubleSlabMangrove() {
        this(0);
    }

    public BlockDoubleSlabMangrove(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return MANGROVE_DOUBLE_SLAB;
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
        return "Double Mangrove Slab";
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(MANGROVE_SLAB));
    }

    @Override
    public Item[] getDrops(Item item) {
        return new Item[]{
                Item.get(Item.MANGROVE_SLAB, 0, 2)
        };
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.MANGROVE_BLOCK_COLOR;
    }
}
