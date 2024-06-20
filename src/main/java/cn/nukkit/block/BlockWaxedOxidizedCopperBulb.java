package cn.nukkit.block;

public class BlockWaxedOxidizedCopperBulb extends BlockCopperBulbBase {

    public BlockWaxedOxidizedCopperBulb() {
        this(0);
    }

    public BlockWaxedOxidizedCopperBulb(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Waxed Oxidized Copper Bulb";
    }

    @Override
    public int getId() {
        return WAXED_OXIDIZED_COPPER_BULB;
    }

    @Override
    public int getLightLevel() {
        return this.isLit() ? 4 : 0;
    }
}