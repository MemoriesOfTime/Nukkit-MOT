package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockTrapdoorPoplar extends BlockTrapdoor {

    public BlockTrapdoorPoplar() {
        this(0);
    }

    public BlockTrapdoorPoplar(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return POPLAR_TRAPDOOR;
    }

    @Override
    public String getName() {
        return "Poplar Trapdoor";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.SPRUCE_BLOCK_COLOR;
    }
}
