package cn.nukkit.block;

public class BlockCandleCakeLightGray extends BlockCandleCake {

    public BlockCandleCakeLightGray() {
        this(0);
    }

    public BlockCandleCakeLightGray(int meta) {
        super(meta);
    }

    @Override
    protected String getColorName() {
        return "LightGray";
    }

    @Override
    public int getId() {
        return LIGHT_GRAY_CANDLE_CAKE;
    }

    @Override
    protected BlockCandle toCandleForm() {
        return new BlockCandleLightGray();
    }
}
