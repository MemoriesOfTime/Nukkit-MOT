package cn.nukkit.block;

public class BlockWaxedCopperBulb extends BlockCopperBulbBase {

    public BlockWaxedCopperBulb() {
        this(0);
    }

    public BlockWaxedCopperBulb(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Waxed Copper Bulb";
    }

    @Override
    public int getId() {
        return WAXED_COPPER_BULB;
    }

    @Override
    public int getLightLevel() {
        return this.isLit() ? 15 : 0;
    }
}