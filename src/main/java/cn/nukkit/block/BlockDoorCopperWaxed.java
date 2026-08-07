package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

public class BlockDoorCopperWaxed extends BlockDoorCopper {

    public BlockDoorCopperWaxed() {
        this(0);
    }

    public BlockDoorCopperWaxed(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Waxed Copper Door";
    }

    @Override
    public int getId() {
        return WAXED_COPPER_DOOR;
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
