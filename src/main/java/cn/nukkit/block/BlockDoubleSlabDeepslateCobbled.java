package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemTool;

public class BlockDoubleSlabDeepslateCobbled extends BlockSolidMeta {

    public BlockDoubleSlabDeepslateCobbled() {
        this(0);
    }

    public BlockDoubleSlabDeepslateCobbled(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return COBBLED_DEEPSLATE_DOUBLE_SLAB;
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
        return new ItemBlock(Block.get(COBBLED_DEEPSLATE_SLAB));
    }

    @Override
    public Item[] getDrops(Item item) {
        return new Item[]{
                Item.get(Item.COBBLED_DEEPSLATE_SLAB, 0, 2)
        };
    }
}
