package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import cn.nukkit.utils.BlockColor;
import org.jetbrains.annotations.NotNull;

public class BlockWaxedCopperBulb extends BlockCopperBulbBase {

    public BlockWaxedCopperBulb() {
        this(0);
    }

    public BlockWaxedCopperBulb(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Waxed Copper Bulb";
    }

    @Override
    public int getId() {
        return WAXED_COPPER_BULB;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.ADOBE_BLOCK_COLOR;
    }

    @Override
    public int getLightLevel() {
        return this.isLit() ? 15 : 0;
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.UNAFFECTED;
    }

    @Override
    public boolean isWaxed() {
        return true;
    }
}