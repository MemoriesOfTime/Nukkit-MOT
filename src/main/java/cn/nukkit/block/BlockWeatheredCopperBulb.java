package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockWeatheredCopperBulb extends BlockCopperBulbBase {

    public BlockWeatheredCopperBulb() {
        this(0);
    }

    public BlockWeatheredCopperBulb(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Weathered Copper Bulb";
    }

    @Override
    public int getId() {
        return WEATHERED_COPPER_BULB;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.WARPED_STEM_BLOCK_COLOR;
    }

    @Override
    public int getLightLevel() {
        return this.isLit() ? 8 : 0;
    }
}