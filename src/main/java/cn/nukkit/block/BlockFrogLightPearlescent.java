package cn.nukkit.block;

/**
 * @author glorydark
 */
public class BlockFrogLightPearlescent extends BlockFrogLight {

    public BlockFrogLightPearlescent() {
        this(0);
    }

    public BlockFrogLightPearlescent(int meta) {
        super(meta);
    }


    @Override
    public String getName() {
        return "Pearlescent FrogLight";
    }

    @Override
    public int getId() {
        return PEARLESCENT_FROGLIGHT;
    }
}
