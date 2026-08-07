package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockHangingSignPaleOak extends BlockHangingSign {

    public BlockHangingSignPaleOak() {
        this(0);
    }

    public BlockHangingSignPaleOak(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return PALE_OAK_HANGING_SIGN;
    }

    @Override
    public String getName() {
        return "Pale Oak Hanging Sign";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.QUARTZ_BLOCK_COLOR;
    }
}
