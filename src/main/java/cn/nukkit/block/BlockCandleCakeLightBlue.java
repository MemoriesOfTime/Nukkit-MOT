package cn.nukkit.block;

public class BlockCandleCakeLightBlue extends BlockCandleCake {

    public BlockCandleCakeLightBlue() {
        this(0);
    }

    public BlockCandleCakeLightBlue(int meta) {
        super(meta);
    }

    @Override
    protected String getColorName() {
        return "LightBlue";
    }

    @Override
    public int getId() {
        return LIGHT_BLUE_CANDLE_CAKE;
    }

    @Override
    protected BlockCandle toCandleForm() {
        return new BlockCandleLightBlue();
    }
}
