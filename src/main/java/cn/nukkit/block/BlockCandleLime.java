package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

/**
 * @author Gabriel8579
 * @since 2021-08-13
 */


public class BlockCandleLime extends BlockCandle {

    public BlockCandleLime() { this(0); }

    public BlockCandleLime(int meta) { super(meta); }

    @Override
    public int getId() {
        return BlockID.LIME_CANDLE;
    }

    @Override
    public String getName() {
        return "Lime Candle";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.LIME_BLOCK_COLOR;
    }

    @Override
    protected Block toCakeForm() {
        return new BlockCandleCakeLime();
    }
}