package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

/**
 * @author Gabriel8579
 * @since 2021-08-13
 */


public class BlockCandleRed extends BlockCandle {

    public BlockCandleRed() { this(0); }

    public BlockCandleRed(int meta) { super(meta); }

    @Override
    public int getId() {
        return BlockID.RED_CANDLE;
    }

    @Override
    public String getName() {
        return "Red Candle";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.RED_BLOCK_COLOR;
    }

    @Override
    protected Block toCakeForm() {
        return new BlockCandleCakeRed();
    }
}