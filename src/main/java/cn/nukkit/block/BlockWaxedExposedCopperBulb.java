package cn.nukkit.block;

public class BlockWaxedExposedCopperBulb extends BlockCopperBulbBase {

    public BlockWaxedExposedCopperBulb() {
        this(0);
    }

    public BlockWaxedExposedCopperBulb(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Waxed Exposed Copper Bulb";
    }

    @Override
    public int getId() {
        return WAXED_EXPOSED_COPPER_BULB;
    }

    @Override
    public int getLightLevel() {
        return 12;
    }
}