package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockTrapdoorBamboo extends BlockTrapdoor {
    public BlockTrapdoorBamboo() {
        this(0);
    }

    public BlockTrapdoorBamboo(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return BAMBOO_TRAPDOOR;
    }

    @Override
    public String getName() {
        return "Bamboo Trapdoor";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.BAMBOO_BLOCK_COLOR;
    }
}
