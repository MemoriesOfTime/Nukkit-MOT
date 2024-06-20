package cn.nukkit.block;

public class BlockWeatheredCopperBulb extends BlockCopperBulbBase {

    public BlockWeatheredCopperBulb() {
        this(0);
    }

    public BlockWeatheredCopperBulb(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Weathered Copper Bulb";
    }

    @Override
    public int getId() {
        return WEATHERED_COPPER_BULB;
    }

    @Override
    public int getLightLevel() {
        return this.isLit() ? 8 : 0;
    }
}