package cn.nukkit.block;

public class BlockOxidizedCopperBulb extends BlockCopperBulbBase {

    public BlockOxidizedCopperBulb() {
        this(0);
    }

    public BlockOxidizedCopperBulb(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Oxidized Copper Bulb";
    }

    @Override
    public int getId() {
        return OXIDIZED_COPPER_BULB;
    }

    @Override
    public int getLightLevel() {
        return 4;
    }
}