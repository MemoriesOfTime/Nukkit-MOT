package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemTool;

public class BlockDoubleSlabDeepslateTile extends BlockSolidMeta {

    public BlockDoubleSlabDeepslateTile() {
        this(0);
    }

    public BlockDoubleSlabDeepslateTile(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return DEEPSLATE_TILE_DOUBLE_SLAB;
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
        return new ItemBlock(Block.get(DEEPSLATE_TILE_SLAB), this.getDamage() & 0x07);
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
