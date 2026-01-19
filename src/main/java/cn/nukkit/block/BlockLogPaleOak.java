package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockLogPaleOak extends BlockWood {

    public BlockLogPaleOak() {
        this(0);
    }

    public BlockLogPaleOak(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return PALE_OAK_LOG;
    }

    @Override
    public String getName() {
        return "Pale Oak Log";
    }

    @Override
    public int getBurnAbility() {
        return 5;
    }

    @Override
    protected int getStrippedId() {
        return STRIPPED_PALE_OAK_LOG;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.QUARTZ_BLOCK_COLOR;
    }
}
