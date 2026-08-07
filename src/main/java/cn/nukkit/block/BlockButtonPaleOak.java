package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockButtonPaleOak extends BlockButtonWooden {

    public BlockButtonPaleOak() {
        this(0);
    }

    public BlockButtonPaleOak(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return PALE_OAK_BUTTON;
    }

    @Override
    public String getName() {
        return "Pale Oak Button";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.QUARTZ_BLOCK_COLOR;
    }
}
