package cn.nukkit.block;

public class BlockCandleCakeRed extends BlockCandleCake {

    public BlockCandleCakeRed() {
        this(0);
    }

    public BlockCandleCakeRed(int meta) {
        super(meta);
    }

    @Override
    protected String getColorName() {
        return "Red";
    }

    @Override
    public int getId() {
        return RED_CANDLE_CAKE;
    }

    @Override
    protected BlockCandle toCandleForm() {
        return new BlockCandleRed();
    }
}
