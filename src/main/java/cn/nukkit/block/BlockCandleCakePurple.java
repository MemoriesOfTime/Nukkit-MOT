package cn.nukkit.block;

public class BlockCandleCakePurple extends BlockCandleCake {

    public BlockCandleCakePurple() {
        this(0);
    }

    public BlockCandleCakePurple(int meta) {
        super(meta);
    }

    @Override
    protected String getColorName() {
        return "Purple";
    }

    @Override
    public int getId() {
        return PURPLE_CANDLE_CAKE;
    }

    @Override
    protected BlockCandle toCandleForm() {
        return new BlockCandlePurple();
    }
}
