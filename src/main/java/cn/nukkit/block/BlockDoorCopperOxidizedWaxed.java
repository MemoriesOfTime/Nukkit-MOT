package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

public class BlockDoorCopperOxidizedWaxed extends BlockDoorCopper {

    public BlockDoorCopperOxidizedWaxed() {
        this(0);
    }

    public BlockDoorCopperOxidizedWaxed(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Waxed Oxidized Copper Door";
    }

    @Override
    public int getId() {
        return WAXED_OXIDIZED_COPPER_DOOR;
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
