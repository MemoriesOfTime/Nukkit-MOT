package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockWaxedExposedCopperBulb extends BlockCopperBulbBase {

    public BlockWaxedExposedCopperBulb() {
        this(0);
    }

    public BlockWaxedExposedCopperBulb(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Waxed Exposed Copper Bulb";
    }

    @Override
    public int getId() {
        return WAXED_EXPOSED_COPPER_BULB;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.LIGHT_GRAY_TERRACOTA_BLOCK_COLOR;
    }

    @Override
    public int getLightLevel() {
        return this.isLit() ? 12 : 0;
    }
}