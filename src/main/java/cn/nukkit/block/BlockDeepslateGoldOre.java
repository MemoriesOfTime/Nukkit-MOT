package cn.nukkit.block;

public class BlockDeepslateGoldOre extends BlockOreGold {
    @Override
    public int getId() {
        return DEEPSLATE_GOLD_ORE;
    }

    @Override
    public double getHardness() {
        return 4.5;
    }

    @Override
    public String getName() {
        return "Deepslate Gold Ore";
    }
}
