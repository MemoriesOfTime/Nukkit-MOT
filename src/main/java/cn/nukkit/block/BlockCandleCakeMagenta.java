package cn.nukkit.block;

public class BlockCandleCakeMagenta extends BlockCandleCake {

    public BlockCandleCakeMagenta() {
        this(0);
    }

    public BlockCandleCakeMagenta(int meta) {
        super(meta);
    }

    @Override
    protected String getColorName() {
        return "Magenta";
    }

    @Override
    public int getId() {
        return MAGENTA_CANDLE_CAKE;
    }

    @Override
    protected BlockCandle toCandleForm() {
        return new BlockCandleMagenta();
    }
}
