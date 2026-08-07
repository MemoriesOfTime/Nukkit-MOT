package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

public class BlockTrapdoorCopperExposed extends BlockTrapdoorCopper {

    public BlockTrapdoorCopperExposed() {
        this(0);
    }

    public BlockTrapdoorCopperExposed(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Exposed Copper Trapdoor";
    }

    @Override
    public int getId() {
        return EXPOSED_COPPER_TRAPDOOR;
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.EXPOSED;
    }
}
