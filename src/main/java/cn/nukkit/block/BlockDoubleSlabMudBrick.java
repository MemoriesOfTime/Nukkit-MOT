package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemTool;

public class BlockDoubleSlabMudBrick extends BlockSolidMeta {

    public BlockDoubleSlabMudBrick() {
        this(0);
    }

    public BlockDoubleSlabMudBrick(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return MUD_BRICK_DOUBLE_SLAB;
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
        return ItemTool.TYPE_PICKAXE;
    }

    @Override
    public int getToolTier() {
        return ItemTool.TIER_WOODEN;
    }

    @Override
    public String getName() {
        return "Double Mud Brick Slab";
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(MUD_BRICK_SLAB));
    }

    @Override
    public Item[] getDrops(Item item) {
        return new Item[]{
                Item.get(Item.MUD_BRICK_SLAB, 0, 2)
        };
    }
}
