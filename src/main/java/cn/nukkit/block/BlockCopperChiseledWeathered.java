package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

public class BlockCopperChiseledWeathered extends BlockCopperChiseled {
    public BlockCopperChiseledWeathered() {
        // Does nothing
    }

    @Override
    public String getName() {
        return "Weathered Chiseled Copper";
    }

    @Override
    public int getId() {
        return WEATHERED_CHISELED_COPPER;
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.WEATHERED;
    }
}
