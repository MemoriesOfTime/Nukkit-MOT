package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemTool;
import cn.nukkit.utils.BlockColor;
import cn.nukkit.utils.DyeColor;

public abstract class BlockDoubleSlabConcrete extends BlockSolidMeta {

    protected BlockDoubleSlabConcrete(int meta) {
        super(meta);
    }

    public abstract DyeColor getDyeColor();

    protected abstract int getSingleSlabId();

    @Override
    public String getName() {
        return getDyeColor().getName() + " Concrete Double Slab";
    }

    @Override
    public double getHardness() {
        return 1.8;
    }

    @Override
    public double getResistance() {
        return 9;
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
    public Item toItem() {
        return new ItemBlock(Block.get(getSingleSlabId()), 0);
    }

    @Override
    public Item[] getDrops(Item item) {
        if (item.isPickaxe() && item.getTier() >= ItemTool.TIER_WOODEN) {
            Item slab = toItem();
            slab.setCount(2);
            return new Item[]{slab};
        }
        return Item.EMPTY_ARRAY;
    }

    @Override
    public boolean canHarvestWithHand() {
        return false;
    }

    @Override
    public BlockColor getColor() {
        return getDyeColor().getColor();
    }
}
