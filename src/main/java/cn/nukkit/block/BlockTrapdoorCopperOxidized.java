package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

public class BlockTrapdoorCopperOxidized extends BlockTrapdoorCopper {

    public BlockTrapdoorCopperOxidized() {
        this(0);
    }

    public BlockTrapdoorCopperOxidized(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Oxidized Copper Trapdoor";
    }

    @Override
    public int getId() {
        return OXIDIZED_COPPER_TRAPDOOR;
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.OXIDIZED;
    }
}
