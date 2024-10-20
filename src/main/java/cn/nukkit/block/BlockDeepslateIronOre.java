package cn.nukkit.block;

public class BlockDeepslateIronOre extends BlockOreIron {
    @Override
    public int getId() {
        return DEEPSLATE_IRON_ORE;
    }

    @Override
    public double getHardness() {
        return 4.5;
    }

    @Override
    public String getName() {
        return "Deepslate Iron Ore";
    }
}
