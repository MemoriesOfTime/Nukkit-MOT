package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

/**
 * @author Gabriel8579
 * @since 2021-08-13
 */


public class BlockCandleBlue extends BlockCandle {

    public BlockCandleBlue() { this(0); }

    public BlockCandleBlue(int meta) { super(meta); }

    @Override
    public int getId() {
        return BlockID.BLUE_CANDLE;
    }

    @Override
    public String getName() {
        return "Blue Candle";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.BLUE_BLOCK_COLOR;
    }

    @Override
    protected Block toCakeForm() {
        return new BlockCandleCakeBlue();
    }
}