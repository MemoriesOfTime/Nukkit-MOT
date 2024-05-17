package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;

public class BlockFenceGateCrimson extends BlockFenceGate {
    @Override
    public int getId() {
        return CRIMSON_FENCE_GATE;
    }

    @Override
    public String getName() {
        return "Crimson Fence Gate";
    }

    public BlockFenceGateCrimson() {
        this(0);
    }

    public BlockFenceGateCrimson(int meta) {
        super(meta);
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(this.getId(), 0), 0);
    }
}