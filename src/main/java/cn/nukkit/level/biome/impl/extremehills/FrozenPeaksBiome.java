package cn.nukkit.level.biome.impl.extremehills;

/**
 * @author Kanelucky
 */
public class FrozenPeaksBiome extends ExtremeHillsBiome {

    public FrozenPeaksBiome() {
        super(false);

        this.setBaseHeight(1.6F);
        this.setHeightVariation(0.45F);
    }

    @Override
    public String getName() {
        return "Frozen Peaks";
    }

    @Override
    public boolean isFreezing() {
        return true;
    }
}
