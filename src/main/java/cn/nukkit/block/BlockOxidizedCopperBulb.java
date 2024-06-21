package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockOxidizedCopperBulb extends BlockCopperBulbBase {

    public BlockOxidizedCopperBulb() {
        this(0);
    }

    public BlockOxidizedCopperBulb(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Oxidized Copper Bulb";
    }

    @Override
    public int getId() {
        return OXIDIZED_COPPER_BULB;
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