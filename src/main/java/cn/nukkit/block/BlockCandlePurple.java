package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

/**
 * @author Gabriel8579
 * @since 2021-08-13
 */
public class BlockCandlePurple extends BlockCandle {

    public BlockCandlePurple() {
        this(0);
    }

    public BlockCandlePurple(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return BlockID.PURPLE_CANDLE;
    }

    @Override
    public String getName() {
        return "Purple Candle";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.PURPLE_BLOCK_COLOR;
    }

    @Override
    protected Block toCakeForm() {
        return new BlockCandleCakePurple();
    }
}