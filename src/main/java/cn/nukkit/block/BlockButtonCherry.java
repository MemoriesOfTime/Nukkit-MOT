package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockButtonCherry extends BlockButtonWooden {
    public BlockButtonCherry() {
        this(0);
    }

    public BlockButtonCherry(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return CHERRY_BUTTON;
    }

    @Override
    public String getName() {
        return "Cherry Button";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.CHERRY_BLOCK_COLOR;
    }
}
