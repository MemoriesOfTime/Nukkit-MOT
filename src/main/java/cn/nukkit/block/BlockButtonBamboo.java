package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockButtonBamboo extends BlockButtonWooden {
    public BlockButtonBamboo() {
        this(0);
    }

    public BlockButtonBamboo(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return BAMBOO_BUTTON;
    }

    @Override
    public String getName() {
        return "Bamboo Button";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.BAMBOO_BLOCK_COLOR;
    }
}
