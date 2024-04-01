package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemTool;
import cn.nukkit.utils.BlockColor;

public class BlockAncientDebris extends BlockSolid {

    public BlockAncientDebris() {
        super();
    }

    @Override
    public int getId() {
        return ANCIENT_DEBRIS;
    }

    @Override
    public String getName() {
        return "Ancient Debris";
    }

    @Override
    public double getResistance() {
        return 1200;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_PICKAXE;
    }

    @Override
    public double getHardness() {
        return 30;
    }

    @Override
    public Item[] getDrops(final Item item) {
        if (item.isPickaxe()) {
            return new Item[]{new ItemBlock(this, 0, 1)};
        }
        return Item.EMPTY_ARRAY;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.BLACK_BLOCK_COLOR;
    }

    @Override
    public boolean canHarvestWithHand() {
        return false;
    }

    @Override
    public int getToolTier() {
        return ItemTool.TIER_DIAMOND;
    }
}
