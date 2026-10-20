package cn.nukkit.block;

/**
 * @author glorydark
 */
public class BlockFrogLightOchre extends BlockFrogLight {

    public BlockFrogLightOchre() {
        this(0);
    }

    public BlockFrogLightOchre(int meta) {
        super(meta);
    }


    @Override
    public String getName() {
        return "Ochre FrogLight";
    }

    @Override
    public int getId() {
        return OCHRE_FROGLIGHT;
    }
}