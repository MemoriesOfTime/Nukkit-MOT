package cn.nukkit.level.biome.impl.extremehills;

/**
 * @author Kanelucky
 */
public class JaggedPeaksBiome extends ExtremeHillsBiome {

    public JaggedPeaksBiome() {
        super(false);
    }

    @Override
    public String getName() {
        return "Jagged Peaks";
    }

    @Override
    public boolean isFreezing() {
        return true;
    }
}
