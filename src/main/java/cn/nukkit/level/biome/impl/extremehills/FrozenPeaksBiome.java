package cn.nukkit.level.biome.impl.extremehills;

/**
 * @author Kanelucky
 */
public class FrozenPeaksBiome extends ExtremeHillsBiome {

    public FrozenPeaksBiome() {
        super(false);
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
