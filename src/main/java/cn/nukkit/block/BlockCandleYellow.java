package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

/**
 * @author Gabriel8579
 * @since 2021-08-13
 */
public class BlockCandleYellow extends BlockCandle {

    public BlockCandleYellow() {
        this(0);
    }

    public BlockCandleYellow(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return BlockID.YELLOW_CANDLE;
    }

    @Override
    public String getName() {
        return "Yellow Candle";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.YELLOW_BLOCK_COLOR;
    }

    @Override
    protected Block toCakeForm() {
        return new BlockCandleCakeYellow();
    }
}