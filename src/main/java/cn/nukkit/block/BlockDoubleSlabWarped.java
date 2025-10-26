package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.utils.BlockColor;

public class BlockDoubleSlabWarped extends BlockSolid {
    public BlockDoubleSlabWarped() {
        
    }

    @Override
    public int getId() {
        return WARPED_DOUBLE_SLAB;
    }

    @Override
    public String getName() {
        return "Warped Double Slab";
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
        return new ItemBlock(Block.get(WARPED_SLAB), this.getDamage() & 0x07);
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
    
    @Override
    public BlockColor getColor() {
        return BlockColor.WARPED_STEM_BLOCK_COLOR;
    }
}
