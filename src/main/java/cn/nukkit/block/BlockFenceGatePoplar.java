package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.utils.BlockColor;

public class BlockFenceGatePoplar extends BlockFenceGate {

    public BlockFenceGatePoplar() {
        this(0);
    }

    public BlockFenceGatePoplar(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return POPLAR_FENCE_GATE;
    }

    @Override
    public String getName() {
        return "Poplar Fence Gate";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.SPRUCE_BLOCK_COLOR;
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(this.getId(), 0), 0);
    }
}
