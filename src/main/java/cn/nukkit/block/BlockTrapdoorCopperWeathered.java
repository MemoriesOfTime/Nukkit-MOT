package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

public class BlockTrapdoorCopperWeathered extends BlockTrapdoorCopper {

    public BlockTrapdoorCopperWeathered() {
        this(0);
    }

    public BlockTrapdoorCopperWeathered(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Weathered Copper Trapdoor";
    }

    @Override
    public int getId() {
        return WEATHERED_COPPER_TRAPDOOR;
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.WEATHERED;
    }
}
