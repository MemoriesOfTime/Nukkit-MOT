package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockHangingSignBamboo extends BlockHangingSign {

    public BlockHangingSignBamboo() {
        this(0);
    }

    public BlockHangingSignBamboo(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return BAMBOO_HANGING_SIGN;
    }

    @Override
    public String getName() {
        return "Bamboo Hanging Sign";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.YELLOW_BLOCK_COLOR;
    }
}
