package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockHangingSignWarped extends BlockHangingSign {

    public BlockHangingSignWarped() {
        this(0);
    }

    public BlockHangingSignWarped(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return WARPED_HANGING_SIGN;
    }

    @Override
    public String getName() {
        return "Warped Hanging Sign";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.WARPED_STEM_BLOCK_COLOR;
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
