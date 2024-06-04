package cn.nukkit.block;

public class BlockHyphaeWarped extends BlockStem {

    public BlockHyphaeWarped() {
        this(0);
    }

    public BlockHyphaeWarped(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return WARPED_HYPHAE;
    }

    @Override
    public String getName() {
        return "Warped Hyphae";
    }

    @Override
    public int getStrippedId() {
        return STRIPPED_WARPED_HYPHAE;
    }

}
