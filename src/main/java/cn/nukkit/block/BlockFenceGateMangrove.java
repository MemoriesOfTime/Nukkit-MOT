package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.utils.BlockColor;

public class BlockFenceGateMangrove extends BlockFenceGate {

    public BlockFenceGateMangrove() {
        this(0);
    }

    public BlockFenceGateMangrove(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return MANGROVE_FENCE_GATE;
    }

    @Override
    public String getName() {
        return "Mangrove Fence Gate";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.MANGROVE_BLOCK_COLOR;
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(this.getId(), 0), 0);
    }
}