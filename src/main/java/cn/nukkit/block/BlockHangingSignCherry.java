package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockHangingSignCherry extends BlockHangingSign {

    public BlockHangingSignCherry() {
        this(0);
    }

    public BlockHangingSignCherry(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return CHERRY_HANGING_SIGN;
    }

    @Override
    public String getName() {
        return "Cherry Hanging Sign";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.PINK_BLOCK_COLOR;
    }
}
