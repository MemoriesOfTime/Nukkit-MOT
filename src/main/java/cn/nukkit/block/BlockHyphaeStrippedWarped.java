package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockHyphaeStrippedWarped extends BlockStemStripped {

    public BlockHyphaeStrippedWarped() {
        
    }

    @Override
    public int getId() {
        return STRIPPED_WARPED_HYPHAE;
    }
    
    @Override
    public String getName() {
        return "Warped Stripped Hyphae";
    }

    @Override
    public double getHardness() {
        return 0.4;
    }
    
    @Override
    public BlockColor getColor() {
        return BlockColor.WARPED_STEM_BLOCK_COLOR;
    }

}
