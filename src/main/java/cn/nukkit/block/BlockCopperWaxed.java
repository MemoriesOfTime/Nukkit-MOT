package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

/**
 * @author LoboMetalurgico
 * @since 11/06/2021
 */
public class BlockCopperWaxed extends BlockCopperBase {
    public BlockCopperWaxed() {
        // Does nothing
    }

    @Override
    public String getName() {
        return "Waxed Block of Copper";
    }

    @Override
    public int getId() {
        return WAXED_COPPER;
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.UNAFFECTED;
    }

    @Override
    public boolean isWaxed() {
        return true;
    }
}