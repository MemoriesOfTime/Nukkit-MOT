package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockHangingSignCrimson extends BlockHangingSign {

    public BlockHangingSignCrimson() {
        this(0);
    }

    public BlockHangingSignCrimson(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return CRIMSON_HANGING_SIGN;
    }

    @Override
    public String getName() {
        return "Crimson Hanging Sign";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.CRIMSON_STEM_BLOCK_COLOR;
    }

    @Override
    public int getBurnChance() {
        return 0;
    }

    @Override
    public int getBurnAbility() {
        return 0;
    }
}
