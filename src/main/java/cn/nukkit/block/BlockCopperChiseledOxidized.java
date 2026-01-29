package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

public class BlockCopperChiseledOxidized extends BlockCopperChiseled {
    public BlockCopperChiseledOxidized() {
        // Does nothing
    }

    @Override
    public String getName() {
        return "Oxidized Chiseled Copper";
    }

    @Override
    public int getId() {
        return OXIDIZED_CHISELED_COPPER;
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.OXIDIZED;
    }
}
