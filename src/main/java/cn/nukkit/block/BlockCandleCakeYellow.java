package cn.nukkit.block;

public class BlockCandleCakeYellow extends BlockCandleCake {

    public BlockCandleCakeYellow() {
        this(0);
    }

    public BlockCandleCakeYellow(int meta) {
        super(meta);
    }

    @Override
    protected String getColorName() {
        return "Yellow";
    }

    @Override
    public int getId() {
        return YELLOW_CANDLE_CAKE;
    }

    @Override
    protected BlockCandle toCandleForm() {
        return new BlockCandleYellow();
    }
}
