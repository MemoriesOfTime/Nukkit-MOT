package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockHangingSignJungle extends BlockHangingSign {

    public BlockHangingSignJungle() {
        this(0);
    }

    public BlockHangingSignJungle(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return JUNGLE_HANGING_SIGN;
    }

    @Override
    public String getName() {
        return "Jungle Hanging Sign";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.DIRT_BLOCK_COLOR;
    }
}
