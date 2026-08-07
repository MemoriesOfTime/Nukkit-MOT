package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

public class BlockCopperChiseledOxidizedWaxed extends BlockCopperChiseled {
    public BlockCopperChiseledOxidizedWaxed() {
        // Does nothing
    }

    @Override
    public String getName() {
        return "Waxed Oxidized Chiseled Copper";
    }

    @Override
    public int getId() {
        return WAXED_OXIDIZED_CHISELED_COPPER;
    }

    @Override
    public boolean isWaxed() {
        return true;
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.OXIDIZED;
    }
}
