package cn.nukkit.level.biome.impl.taiga;

import cn.nukkit.block.Block;
import cn.nukkit.level.generator.populator.impl.WaterIcePopulator;

/**
 * Snowy taiga on mountain sides, with a snow-covered surface and freezing water.
 *
 * @author Kanelucky
 */
public class GroveBiome extends TaigaHillsBiome {

    public GroveBiome() {
        super();

        this.addPopulator(new WaterIcePopulator());

        this.setBaseHeight(1.0F);
        this.setHeightVariation(0.30F);
    }

    @Override
    public String getName() {
        return "Grove";
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
