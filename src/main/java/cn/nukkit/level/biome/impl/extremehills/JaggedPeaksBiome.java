package cn.nukkit.level.biome.impl.extremehills;

import cn.nukkit.block.Block;
import cn.nukkit.level.generator.populator.impl.WaterIcePopulator;

/**
 * The highest and sharpest mountain-peak biome, permanently frozen with a snow-covered surface.
 *
 * @author Kanelucky
 */
public class JaggedPeaksBiome extends ExtremeHillsBiome {

    public JaggedPeaksBiome() {
        super(false);

        this.addPopulator(new WaterIcePopulator());

        this.setBaseHeight(1.8F);
        this.setHeightVariation(0.6F);
    }

    @Override
    public String getName() {
        return "Jagged Peaks";
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
