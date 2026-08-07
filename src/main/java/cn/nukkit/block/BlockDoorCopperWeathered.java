package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

public class BlockDoorCopperWeathered extends BlockDoorCopper {

    public BlockDoorCopperWeathered() {
        this(0);
    }

    public BlockDoorCopperWeathered(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Weathered Copper Door";
    }

    @Override
    public int getId() {
        return WEATHERED_COPPER_DOOR;
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.WEATHERED;
    }
}
