package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemTool;

public class BlockDoubleSlabBlackstonePolished extends BlockSolid {
    public BlockDoubleSlabBlackstonePolished() {
        
    }

    @Override
    public int getId() {
        return POLISHED_BLACKSTONE_DOUBLE_SLAB;
    }

    @Override
    public String getName() {
        return "Polished Blackstone";
    }

    @Override
    public boolean canHarvestWithHand() {
        return false;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_PICKAXE;
    }

    @Override
    public double getHardness() {
        return 2;
    }

    @Override
    public double getResistance() {
        return 6.0;
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(POLISHED_BLACKSTONE_SLAB), this.getDamage() & 0x07);
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
