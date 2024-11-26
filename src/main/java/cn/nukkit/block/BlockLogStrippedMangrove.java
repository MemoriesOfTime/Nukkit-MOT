package cn.nukkit.block;


import cn.nukkit.utils.BlockColor;

public class BlockLogStrippedMangrove extends BlockWoodStripped {

    public BlockLogStrippedMangrove() {
        this(0);
    }

    public BlockLogStrippedMangrove(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Stripped Mangrove Log";
    }

    @Override
    public int getId() {
        return STRIPPED_MANGROVE_LOG;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.RED_BLOCK_COLOR;
    }
}
