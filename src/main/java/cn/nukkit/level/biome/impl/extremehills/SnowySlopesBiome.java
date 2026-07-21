package cn.nukkit.level.biome.impl.extremehills;

/**
 * @author Kanelucky
 */
public class SnowySlopesBiome extends ExtremeHillsBiome {

    public SnowySlopesBiome() {
        super(false);
    }

    @Override
    public String getName() {
        return "Snowy Slopes";
    }

    @Override
    public boolean isFreezing() {
        return true;
    }
}
