package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;

public class BlockFenceWarped extends BlockFence {
    @Override
    public int getId() {
        return WARPED_FENCE;
    }

    @Override
    public int getBurnChance() {
        return 0;
    }

    @Override
    public int getBurnAbility() {
        return 0;
    }

    public String getName() {
        return "Warped Fence";
    }

    public BlockFenceWarped() {
        this(0);
    }

    public BlockFenceWarped(int meta) {
        super(meta);
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(this.getId(), 0), 0);
    }
}