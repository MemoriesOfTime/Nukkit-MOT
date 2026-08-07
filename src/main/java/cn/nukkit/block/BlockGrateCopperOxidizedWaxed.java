package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

public class BlockGrateCopperOxidizedWaxed extends BlockGrateCopper {
    public BlockGrateCopperOxidizedWaxed() {
        // Does nothing
    }

    @Override
    public String getName() {
        return "Waxed Oxidized Copper Grate";
    }

    @Override
    public int getId() {
        return WAXED_OXIDIZED_COPPER_GRATE;
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
