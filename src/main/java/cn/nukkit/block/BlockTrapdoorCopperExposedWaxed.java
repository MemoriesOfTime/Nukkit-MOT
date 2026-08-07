package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

public class BlockTrapdoorCopperExposedWaxed extends BlockTrapdoorCopper {

    public BlockTrapdoorCopperExposedWaxed() {
        this(0);
    }

    public BlockTrapdoorCopperExposedWaxed(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Waxed Exposed Copper Trapdoor";
    }

    @Override
    public int getId() {
        return WAXED_EXPOSED_COPPER_TRAPDOOR;
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
