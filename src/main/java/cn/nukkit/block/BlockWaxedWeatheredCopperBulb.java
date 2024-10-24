package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import cn.nukkit.utils.BlockColor;
import org.jetbrains.annotations.NotNull;

public class BlockWaxedWeatheredCopperBulb extends BlockCopperBulbBase {

    public BlockWaxedWeatheredCopperBulb() {
        this(0);
    }

    public BlockWaxedWeatheredCopperBulb(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Waxed Weathered Copper Bulb";
    }

    @Override
    public int getId() {
        return WAXED_WEATHERED_COPPER_BULB;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.WARPED_STEM_BLOCK_COLOR;
    }

    @Override
    public int getLightLevel() {
        return this.isLit() ? 8 : 0;
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.WEATHERED;
    }

    @Override
    public boolean isWaxed() {
        return true;
    }
}