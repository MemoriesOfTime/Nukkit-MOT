package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemTool;

public class BlockDoubleSlabBlackstone extends BlockSolid {
    public BlockDoubleSlabBlackstone() {
        
    }

    @Override
    public String getName() {
        return "Double Blackstone Slab";
    }

    @Override
    public int getId() {
        return BLACKSTONE_DOUBLE_SLAB;
    }

    @Override
    public double getResistance() {
        return 6;
    }

    @Override
    public double getHardness() {
        return 2;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_PICKAXE;
    }

    @Override
    public boolean canHarvestWithHand() {
        return false;
    }
    
    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(BLACKSTONE_SLAB), this.getDamage() & 0x07);
    }
    
    @Override
    public Item[] getDrops(Item item) {
        if (item.isPickaxe()) {
            Item slab = toItem();
            slab.setCount(2);
            return new Item[]{ slab };
        } else {
            return new Item[0];
        }
    }
}
