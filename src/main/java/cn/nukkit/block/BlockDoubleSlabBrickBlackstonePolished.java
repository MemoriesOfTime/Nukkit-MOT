package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;

public class BlockDoubleSlabBrickBlackstonePolished extends BlockDoubleSlabBlackstonePolished {
    public BlockDoubleSlabBrickBlackstonePolished() {
    
    }

    @Override
    public int getId() {
        return POLISHED_BLACKSTONE_BRICK_DOUBLE_SLAB;
    }

    @Override
    public String getName() {
        return "Polished Blackstone Brick";
    }

    @Override
    public double getHardness() {
        return 2;
    }
    
    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(POLISHED_BLACKSTONE_BRICK_SLAB), this.getDamage() & 0x07);
    }
}
