package cn.nukkit.block;

public class BlockCandleCakeOrange extends BlockCandleCake {

    public BlockCandleCakeOrange() {
        this(0);
    }

    public BlockCandleCakeOrange(int meta) {
        super(meta);
    }

    @Override
    protected String getColorName() {
        return "Orange";
    }

    @Override
    public int getId() {
        return ORANGE_CANDLE_CAKE;
    }

    @Override
    protected BlockCandle toCandleForm() {
        return new BlockCandleOrange();
    }
}
