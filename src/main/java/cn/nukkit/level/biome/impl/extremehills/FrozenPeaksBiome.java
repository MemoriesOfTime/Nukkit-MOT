package cn.nukkit.level.biome.impl.extremehills;

import cn.nukkit.block.Block;
import cn.nukkit.level.generator.populator.impl.WaterIcePopulator;

/**
 * High but relatively smooth mountain-peak biome, with a surface dominated by packed ice.
 *
 * @author Kanelucky
 */
public class FrozenPeaksBiome extends ExtremeHillsBiome {

    public FrozenPeaksBiome() {
        super(false);

        this.addPopulator(new WaterIcePopulator());

        this.setBaseHeight(1.6F);
        this.setHeightVariation(0.45F);
    }

    @Override
    public String getName() {
        return "Frozen Peaks";
    }

    @Override
    public int getSurfaceId(int x, int y, int z) {
        return PACKED_ICE << Block.DATA_BITS;
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
