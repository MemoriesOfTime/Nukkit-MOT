package cn.nukkit.block;

/**
 * Adapted from Nukkit-EC (<a href="https://github.com/EaseCation/Nukkit">Nukkit-EC</a>)
 */
public class BlockDandelionGolden extends BlockDandelion {

    public BlockDandelionGolden() {
        this(0);
    }

    public BlockDandelionGolden(int meta) {
        super(0);
    }

    @Override
    public int getId() {
        return GOLDEN_DANDELION;
    }

    @Override
    public String getName() {
        return "Golden Dandelion";
    }

    @Override
    public boolean canBeActivated() {
        return false;
    }

    @Override
    protected Block getUncommonFlower() {
        return get(this.getId());
    }
}
