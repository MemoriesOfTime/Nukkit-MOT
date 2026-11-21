package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockLogPoplar extends BlockWood {

    public BlockLogPoplar() {
        this(0);
    }

    public BlockLogPoplar(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return POPLAR_LOG;
    }

    @Override
    public String getName() {
        return "Poplar Log";
    }

    @Override
    public int getBurnAbility() {
        return 5;
    }

    @Override
    protected int getStrippedId() {
        return STRIPPED_POPLAR_LOG;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.SPRUCE_BLOCK_COLOR;
    }
}
