package cn.nukkit.block;

public class BlockDeepslateRedstoneOre extends BlockOreRedstone {
    @Override
    public int getId() {
        return DEEPSLATE_REDSTONE_ORE;
    }

    @Override
    public double getHardness() {
        return 4.5;
    }

    @Override
    public String getName() {
        return "Deepslate Redstone Ore";
    }
}
