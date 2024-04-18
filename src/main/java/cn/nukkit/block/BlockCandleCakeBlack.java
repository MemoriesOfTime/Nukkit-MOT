package cn.nukkit.block;

public class BlockCandleCakeBlack extends BlockCandleCake {

    public BlockCandleCakeBlack() {
        this(0);
    }

    public BlockCandleCakeBlack(int meta) {
        super(meta);
    }

    @Override
    protected String getColorName() {
        return "Black";
    }

    @Override
    public int getId() {
        return BLACK_CANDLE_CAKE;
    }

    @Override
    protected BlockCandle toCandleForm() {
        return new BlockCandleBlack();
    }
}
