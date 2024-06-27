package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockWaxedOxidizedCopperBulb extends BlockCopperBulbBase {

    public BlockWaxedOxidizedCopperBulb() {
        this(0);
    }

    public BlockWaxedOxidizedCopperBulb(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Waxed Oxidized Copper Bulb";
    }

    @Override
    public int getId() {
        return WAXED_OXIDIZED_COPPER_BULB;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.WARPED_NYLIUM_BLOCK_COLOR;
    }

    @Override
    public int getLightLevel() {
        return this.isLit() ? 4 : 0;
    }
}