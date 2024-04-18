package cn.nukkit.block;

public class BlockCandleCakeGreen extends BlockCandleCake {

    public BlockCandleCakeGreen() {
        this(0);
    }

    public BlockCandleCakeGreen(int meta) {
        super(meta);
    }

    @Override
    protected String getColorName() {
        return "Green";
    }

    @Override
    public int getId() {
        return GREEN_CANDLE_CAKE;
    }

    @Override
    protected BlockCandle toCandleForm() {
        return new BlockCandleGreen();
    }
}
