package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

public class BlockDoorCopperOxidized extends BlockDoorCopper {

    public BlockDoorCopperOxidized() {
        this(0);
    }

    public BlockDoorCopperOxidized(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Oxidized Copper Door";
    }

    @Override
    public int getId() {
        return OXIDIZED_COPPER_DOOR;
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.OXIDIZED;
    }
}
