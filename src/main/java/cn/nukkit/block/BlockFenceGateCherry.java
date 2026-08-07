package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.utils.BlockColor;

public class BlockFenceGateCherry extends BlockFenceGate {

    public BlockFenceGateCherry() {
        this(0);
    }

    public BlockFenceGateCherry(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return CHERRY_FENCE_GATE;
    }

    @Override
    public String getName() {
        return "Cherry Fence Gate";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.CHERRY_BLOCK_COLOR;
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(this.getId(), 0), 0);
    }
}