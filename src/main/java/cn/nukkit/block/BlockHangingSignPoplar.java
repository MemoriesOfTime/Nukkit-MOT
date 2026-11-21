package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockHangingSignPoplar extends BlockHangingSign {

    public BlockHangingSignPoplar() {
        this(0);
    }

    public BlockHangingSignPoplar(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return POPLAR_HANGING_SIGN;
    }

    @Override
    public String getName() {
        return "Poplar Hanging Sign";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.SPRUCE_BLOCK_COLOR;
    }
}
