package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

public class BlockTrapdoorCopperWeatheredWaxed extends BlockTrapdoorCopper {

    public BlockTrapdoorCopperWeatheredWaxed() {
        this(0);
    }

    public BlockTrapdoorCopperWeatheredWaxed(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Waxed Weathered Copper Trapdoor";
    }

    @Override
    public int getId() {
        return WAXED_WEATHERED_COPPER_TRAPDOOR;
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
