package cn.nukkit.level.biome.impl.extremehills;

import cn.nukkit.block.Block;
import cn.nukkit.level.generator.populator.impl.WaterIcePopulator;

/**
 * Snowy mountain slope with no trees and occasional pumpkins.
 *
 * @author Kanelucky
 */
public class SnowySlopesBiome extends ExtremeHillsBiome {

    public SnowySlopesBiome() {
        super(false);

        this.addPopulator(new WaterIcePopulator());

        this.setBaseHeight(1.3F);
        this.setHeightVariation(0.35F);
    }

    @Override
    public String getName() {
        return "Snowy Slopes";
    }

    @Override
    public int getCoverId(int x, int z) {
        return SNOW_LAYER << Block.DATA_BITS;
    }

    @Override
    public boolean isFreezing() {
        return true;
    }

    @Override
    public boolean canRain() {
        return false;
    }
}
