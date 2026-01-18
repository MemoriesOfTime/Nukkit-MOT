package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

public class BlockTrapdoorCopperOxidizedWaxed extends BlockTrapdoorCopper {

    public BlockTrapdoorCopperOxidizedWaxed() {
        this(0);
    }

    public BlockTrapdoorCopperOxidizedWaxed(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Waxed Oxidized Copper Trapdoor";
    }

    @Override
    public int getId() {
        return WAXED_OXIDIZED_COPPER_TRAPDOOR;
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
