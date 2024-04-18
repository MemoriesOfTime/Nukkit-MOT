package cn.nukkit.block;

public class BlockCandleCakeGray extends BlockCandleCake {

    public BlockCandleCakeGray() {
        this(0);
    }

    public BlockCandleCakeGray(int meta) {
        super(meta);
    }

    @Override
    protected String getColorName() {
        return "Gray";
    }

    @Override
    public int getId() {
        return GRAY_CANDLE_CAKE;
    }

    @Override
    protected BlockCandle toCandleForm() {
        return new BlockCandleGray();
    }
}
