package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemTool;
import cn.nukkit.utils.BlockColor;

public class BlockDoubleSlabCrimson extends BlockSolid {
    public BlockDoubleSlabCrimson() {

    }

    @Override
    public int getId() {
        return CRIMSON_DOUBLE_SLAB;
    }

    @Override
    public String getName() {
        return "Crimson Double Slab";
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
    public Item toItem() {
        return new ItemBlock(Block.get(CRIMSON_SLAB), this.getDamage() & 0x07);
    }

    @Override
    public Item[] getDrops(Item item) {
        if (item.isPickaxe() && item.getTier() >= ItemTool.TIER_STONE) {
            return new Item[]{
                new ItemBlock(Block.get(CRIMSON_SLAB), 0, 2)
            };
        }
        return Item.EMPTY_ARRAY;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.CRIMSON_STEM_BLOCK_COLOR;
    }
}
