package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockStairsCrimson extends BlockStairsWood {

    public BlockStairsCrimson() {
        this(0);
    }

    public BlockStairsCrimson(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return CRIMSON_STAIRS;
    }

    @Override
    public String getName() {
        return "Crimson Stairs";
    }

    @Override
    public int getBurnChance() {
        return 0;
    }

    @Override
    public int getBurnAbility() {
        return 0;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.CRIMSON_STEM_BLOCK_COLOR;
    }
}
