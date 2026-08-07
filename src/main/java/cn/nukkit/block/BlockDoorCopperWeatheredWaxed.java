package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

public class BlockDoorCopperWeatheredWaxed extends BlockDoorCopper {

    public BlockDoorCopperWeatheredWaxed() {
        this(0);
    }

    public BlockDoorCopperWeatheredWaxed(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Waxed Weathered Copper Door";
    }

    @Override
    public int getId() {
        return WAXED_WEATHERED_COPPER_DOOR;
    }

    @Override
    public boolean isWaxed() {
        return true;
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.WEATHERED;
    }
}
