package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockButtonMangrove extends BlockButtonWooden {
    public BlockButtonMangrove() {
        this(0);
    }

    public BlockButtonMangrove(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return MANGROVE_BUTTON;
    }

    @Override
    public String getName() {
        return "Mangrove Button";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.MANGROVE_BLOCK_COLOR;
    }
}
