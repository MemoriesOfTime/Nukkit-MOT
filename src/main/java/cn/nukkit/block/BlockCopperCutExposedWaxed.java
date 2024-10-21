package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;

/**
 * @author LoboMetalurgico
 * @since 11/06/2021
 */
public class BlockCopperCutExposedWaxed extends BlockCopperCut {
    public BlockCopperCutExposedWaxed() {
        // Does nothing
    }

    @Override
    public String getName() {
        return "Waxed Exposed Cut Copper";
    }

    @Override
    public int getId() {
        return WAXED_EXPOSED_CUT_COPPER;
    }

    @Override
    public OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.EXPOSED;
    }

    @Override
    public boolean isWaxed() {
        return true;
    }
}