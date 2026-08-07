package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

public class BlockCopperChiseledExposed extends BlockCopperChiseled {
    public BlockCopperChiseledExposed() {
        // Does nothing
    }

    @Override
    public String getName() {
        return "Exposed Chiseled Copper";
    }

    @Override
    public int getId() {
        return EXPOSED_CHISELED_COPPER;
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.EXPOSED;
    }
}
