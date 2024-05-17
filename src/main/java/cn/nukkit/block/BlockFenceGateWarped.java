package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;

public class BlockFenceGateWarped extends BlockFenceGate {
    @Override
    public int getId() {
        return WARPED_FENCE_GATE;
    }

    @Override
    public String getName() {
        return "Warped Fence Gate";
    }

    public BlockFenceGateWarped() {
        this(0);
    }

    public BlockFenceGateWarped(int meta) {
        super(meta);
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(this.getId(), 0), 0);
    }
}