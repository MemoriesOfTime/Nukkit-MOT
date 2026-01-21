package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockHangingSignBirch extends BlockHangingSign {

    public BlockHangingSignBirch() {
        this(0);
    }

    public BlockHangingSignBirch(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return BIRCH_HANGING_SIGN;
    }

    @Override
    public String getName() {
        return "Birch Hanging Sign";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.SAND_BLOCK_COLOR;
    }
}
