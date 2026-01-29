package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

public class BlockCopperChiseledWeatheredWaxed extends BlockCopperChiseled {
    public BlockCopperChiseledWeatheredWaxed() {
        // Does nothing
    }

    @Override
    public String getName() {
        return "Waxed Weathered Chiseled Copper";
    }

    @Override
    public int getId() {
        return WAXED_WEATHERED_CHISELED_COPPER;
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
