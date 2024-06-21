package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

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
}