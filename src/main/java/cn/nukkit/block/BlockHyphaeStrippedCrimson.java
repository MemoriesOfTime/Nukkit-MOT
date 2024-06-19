package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockHyphaeStrippedCrimson extends BlockStemStripped {

    public BlockHyphaeStrippedCrimson() {
        super();
    }

    public BlockHyphaeStrippedCrimson(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return STRIPPED_CRIMSON_HYPHAE;
    }
    
    @Override
    public String getName() {
        return "Crimson Stripped Hyphae";
    }

    @Override
    public double getHardness() {
        return 0.4;
    }
    
    @Override
    public BlockColor getColor() {
        return BlockColor.CRIMSON_STEM_BLOCK_COLOR;
    }

}
