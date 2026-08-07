package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

public class BlockCopperChiseledExposedWaxed extends BlockCopperChiseled {
    public BlockCopperChiseledExposedWaxed() {
        // Does nothing
    }

    @Override
    public String getName() {
        return "Waxed Exposed Chiseled Copper";
    }

    @Override
    public int getId() {
        return WAXED_EXPOSED_CHISELED_COPPER;
    }

    @Override
    public boolean isWaxed() {
        return true;
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.EXPOSED;
    }
}
