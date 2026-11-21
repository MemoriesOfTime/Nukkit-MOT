package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemTool;
import cn.nukkit.utils.BlockColor;
import cn.nukkit.utils.DyeColor;

public abstract class BlockDoubleSlabWool extends BlockSolidMeta {

    protected BlockDoubleSlabWool(int meta) {
        super(meta);
    }

    public abstract DyeColor getDyeColor();

    protected abstract int getSingleSlabId();

    @Override
    public String getName() {
        return getDyeColor().getName() + " Wool Double Slab";
    }

    @Override
    public double getHardness() {
        return 0.8;
    }

    @Override
    public double getResistance() {
        return 0.8;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_SHEARS;
    }

    @Override
    public int getBurnChance() {
        return 30;
    }

    @Override
    public int getBurnAbility() {
        return 60;
    }

    @Override
    public BlockColor getColor() {
        return getDyeColor().getColor();
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(getSingleSlabId()), 0);
    }

    @Override
    public Item[] getDrops(Item item) {
        Item slab = toItem();
        slab.setCount(2);
        return new Item[]{slab};
    }
}
