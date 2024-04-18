package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

/**
 * @author Gabriel8579
 * @since 2021-08-13
 */


public class BlockCandleLightBlue extends BlockCandle {

    public BlockCandleLightBlue() {
        this(0);
    }

    public BlockCandleLightBlue(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return BlockID.LIGHT_BLUE_CANDLE;
    }

    @Override
    public String getName() {
        return "Light Blue Candle";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.LIGHT_BLUE_BLOCK_COLOR;
    }

    @Override
    protected Block toCakeForm() {
        return new BlockCandleCakeLightBlue();
    }
}