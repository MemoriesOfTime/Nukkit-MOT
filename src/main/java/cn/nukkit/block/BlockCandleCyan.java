package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

/**
 * @author Gabriel8579
 * @since 2021-08-13
 */


public class BlockCandleCyan extends BlockCandle {

    public BlockCandleCyan() {
        this(0);
    }

    public BlockCandleCyan(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return BlockID.CYAN_CANDLE;
    }

    public String getName() {
        return "Cyan Candle";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.CYAN_BLOCK_COLOR;
    }

    @Override
    protected Block toCakeForm() {
        return new BlockCandleCakeCyan();
    }
}