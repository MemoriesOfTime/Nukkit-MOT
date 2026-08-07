package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

public class BlockTrapdoorCopperWaxed extends BlockTrapdoorCopper {

    public BlockTrapdoorCopperWaxed() {
        this(0);
    }

    public BlockTrapdoorCopperWaxed(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Waxed Copper Trapdoor";
    }

    @Override
    public int getId() {
        return WAXED_COPPER_TRAPDOOR;
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
