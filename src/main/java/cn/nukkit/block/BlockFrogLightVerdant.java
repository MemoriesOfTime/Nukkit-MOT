package cn.nukkit.block;

/**
 * @author glorydark
 */
public class BlockFrogLightVerdant extends BlockFrogLight {

    public BlockFrogLightVerdant() {
        this(0);
    }

    public BlockFrogLightVerdant(int meta) {
        super(meta);
    }


    @Override
    public String getName() {
        return "Verdant FrogLight";
    }

    @Override
    public int getId() {
        return VERDANT_FROGLIGHT;
    }
}
