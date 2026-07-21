package cn.nukkit.level.biome.impl.extremehills;

/**
 * @author Kanelucky
 */
public class SnowySlopesBiome extends ExtremeHillsBiome {

    public SnowySlopesBiome() {
        super(false);

        this.setBaseHeight(1.3F);
        this.setHeightVariation(0.35F);
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
