package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

/**
 * @author Gabriel8579
 * @since 2021-08-13
 */


public class BlockCandleGray extends BlockCandle {

    public BlockCandleGray() { this(0); }

    public BlockCandleGray(int meta) { super(meta); }

    @Override
    public int getId() {
        return BlockID.GRAY_CANDLE;
    }

    @Override
    public String getName() {
        return "Gray Candle";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.GRAY_BLOCK_COLOR;
    }

    @Override
    protected Block toCakeForm() {
        return new BlockCandleCakeGray();
    }
}