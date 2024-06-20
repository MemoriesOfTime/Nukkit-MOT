package cn.nukkit.block;

public class BlockWaxedWeatheredCopperBulb extends BlockCopperBulbBase {

    public BlockWaxedWeatheredCopperBulb() {
        this(0);
    }

    public BlockWaxedWeatheredCopperBulb(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Waxed Weathered Copper Bulb";
    }

    @Override
    public int getId() {
        return WAXED_WEATHERED_COPPER_BULB;
    }

    @Override
    public int getLightLevel() {
        return 8;
    }
}