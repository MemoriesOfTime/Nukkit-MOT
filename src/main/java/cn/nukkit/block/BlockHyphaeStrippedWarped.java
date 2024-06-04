package cn.nukkit.block;

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

}
