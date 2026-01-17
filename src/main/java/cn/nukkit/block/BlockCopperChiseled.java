package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BlockCopperChiseled extends BlockCopperBase {
    public BlockCopperChiseled() {
        // Does nothing
    }

    @Override
    public String getName() {
        return "Chiseled Copper";
    }

    @Override
    public int getId() {
        return CHISELED_COPPER;
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.UNAFFECTED;
    }

    @Override
    protected int getCopperId(boolean waxed, @Nullable OxidizationLevel oxidizationLevel) {
        if (oxidizationLevel == null) {
            return getId();
        }
        switch (oxidizationLevel) {
            case UNAFFECTED:
                return waxed ? WAXED_CHISELED_COPPER : CHISELED_COPPER;
            case EXPOSED:
                return waxed ? WAXED_EXPOSED_CHISELED_COPPER : EXPOSED_CHISELED_COPPER;
            case WEATHERED:
                return waxed ? WAXED_WEATHERED_CHISELED_COPPER : WEATHERED_CHISELED_COPPER;
            case OXIDIZED:
                return waxed ? WAXED_OXIDIZED_CHISELED_COPPER : OXIDIZED_CHISELED_COPPER;
            default:
                return getId();
        }
    }
}
