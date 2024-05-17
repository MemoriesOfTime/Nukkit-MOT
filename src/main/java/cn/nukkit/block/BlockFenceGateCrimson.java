package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import lombok.Getter;

@Getter
public class BlockFenceGateCrimson extends BlockFenceGate {
    private final int id = CRIMSON_FENCE_GATE;

    private final String name = "Crimson Fence Gate";

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