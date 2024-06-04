package cn.nukkit.block;

public class BlockHyphaeCrimson extends BlockStem {
    
    public BlockHyphaeCrimson() {
        this(0);
    }

    public BlockHyphaeCrimson(int meta) {
        super(meta);
    }
    
    @Override
    public int getId() {
        return CRIMSON_HYPHAE;
    }
    
    @Override
    public String getName() {
        return "Crimson Hyphae";
    }

    @Override
    public int getStrippedId() {
        return STRIPPED_CRIMSON_HYPHAE;
    }

    @Override
    public double getHardness() {
        return 0.3;
    }

}
