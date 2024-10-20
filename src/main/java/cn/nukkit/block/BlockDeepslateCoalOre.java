package cn.nukkit.block;

public class BlockDeepslateCoalOre extends BlockOreCoal {
    @Override
    public int getId() {
        return DEEPSLATE_COAL_ORE;
    }

    @Override
    public double getHardness() {
        return 4.5;
    }

    @Override
    public String getName() {
        return "Deepslate Coal Ore";
    }
}
