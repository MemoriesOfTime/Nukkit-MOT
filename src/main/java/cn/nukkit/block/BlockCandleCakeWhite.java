package cn.nukkit.block;

public class BlockCandleCakeWhite extends BlockCandleCake {

    public BlockCandleCakeWhite() {
        this(0);
    }

    public BlockCandleCakeWhite(int meta) {
        super(meta);
    }

    @Override
    protected String getColorName() {
        return "White";
    }

    @Override
    public int getId() {
        return WHITE_CANDLE_CAKE;
    }

    @Override
    protected BlockCandle toCandleForm() {
        return new BlockCandleWhite();
    }
}
