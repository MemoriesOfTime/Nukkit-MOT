package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

/**
 * @author Gabriel8579
 * @since 2021-08-13
 */

public class BlockCandleBlack extends BlockCandle {

    public BlockCandleBlack() {
        this(0);
    }

    public BlockCandleBlack(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return BlockID.BLACK_CANDLE;
    }

    @Override
    public String getName() {
        return "Black Candle";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.BLACK_BLOCK_COLOR;
    }

    @Override
    protected Block toCakeForm() {
        return new BlockCandleCakeBlack();
    }
}