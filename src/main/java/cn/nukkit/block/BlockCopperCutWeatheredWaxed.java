package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

/**
 * @author LoboMetalurgico
 * @since 11/06/2021
 */
public class BlockCopperCutWeatheredWaxed extends BlockCopperCut {
    public BlockCopperCutWeatheredWaxed() {
        // Does nothing
    }

    @Override
    public String getName() {
        return "Waxed Weathered Cut Copper";
    }

    @Override
    public int getId() {
        return WAXED_WEATHERED_CUT_COPPER;
    }

    @Override
    public boolean isWaxed() {
        return true;
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.WEATHERED;
    }
}