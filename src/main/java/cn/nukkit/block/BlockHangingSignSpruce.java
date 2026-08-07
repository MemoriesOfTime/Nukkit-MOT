package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockHangingSignSpruce extends BlockHangingSign {

    public BlockHangingSignSpruce() {
        this(0);
    }

    public BlockHangingSignSpruce(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return SPRUCE_HANGING_SIGN;
    }

    @Override
    public String getName() {
        return "Spruce Hanging Sign";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.SPRUCE_BLOCK_COLOR;
    }
}
