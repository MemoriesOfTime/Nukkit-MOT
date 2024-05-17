package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import lombok.Getter;

@Getter
public class BlockFenceGateWarped extends BlockFenceGate {
    private final int id = WARPED_FENCE_GATE;

    private final String name = "Warped Fence Gate";

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