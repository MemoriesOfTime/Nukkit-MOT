package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

public class BlockGrateCopperExposed extends BlockGrateCopper {
    public BlockGrateCopperExposed() {
        // Does nothing
    }

    @Override
    public String getName() {
        return "Exposed Copper Grate";
    }

    @Override
    public int getId() {
        return EXPOSED_COPPER_GRATE;
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.EXPOSED;
    }
}
