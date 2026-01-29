package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

public class BlockDoorCopperExposedWaxed extends BlockDoorCopper {

    public BlockDoorCopperExposedWaxed() {
        this(0);
    }

    public BlockDoorCopperExposedWaxed(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Waxed Exposed Copper Door";
    }

    @Override
    public int getId() {
        return WAXED_EXPOSED_COPPER_DOOR;
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
