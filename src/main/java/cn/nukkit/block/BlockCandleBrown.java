package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

/**
 * @author Gabriel8579
 * @since 2021-08-13
 */


public class BlockCandleBrown extends BlockCandle {

    public BlockCandleBrown() { this(0); }

    public BlockCandleBrown(int meta) { super(meta); }

    @Override
    public int getId() {
        return BlockID.BROWN_CANDLE;
    }

    @Override
    public String getName() {
        return "Brown Candle";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.BROWN_BLOCK_COLOR;
    }

    @Override
    protected Block toCakeForm() {
        return new BlockCandleCakeBrown();
    }
}