package cn.nukkit.block;

public class BlockExposedCopperBulb extends BlockCopperBulbBase {

    public BlockExposedCopperBulb() {
        this(0);
    }

    public BlockExposedCopperBulb(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Exposed Copper Bulb";
    }

    @Override
    public int getId() {
        return EXPOSED_COPPER_BULB;
    }

    @Override
    public int getLightLevel() {
        return this.isLit() ? 12 : 0;
    }
}