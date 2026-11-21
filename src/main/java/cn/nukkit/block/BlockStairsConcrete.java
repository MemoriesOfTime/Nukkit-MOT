package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemTool;
import cn.nukkit.utils.BlockColor;
import cn.nukkit.utils.DyeColor;

public abstract class BlockStairsConcrete extends BlockStairs {

    protected BlockStairsConcrete(int meta) {
        super(meta);
    }

    public abstract DyeColor getDyeColor();

    @Override
    public String getName() {
        return getDyeColor().getName() + " Concrete Stairs";
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
    public Item[] getDrops(Item item) {
        if (item.isPickaxe() && item.getTier() >= ItemTool.TIER_WOODEN) {
            return new Item[]{toItem()};
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
