package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

public class BlockGrateCopperWaxed extends BlockGrateCopper {
    public BlockGrateCopperWaxed() {
        // Does nothing
    }

    @Override
    public String getName() {
        return "Waxed Copper Grate";
    }

    @Override
    public int getId() {
        return WAXED_COPPER_GRATE;
    }

    @Override
    public boolean isWaxed() {
        return true;
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.UNAFFECTED;
    }
}
