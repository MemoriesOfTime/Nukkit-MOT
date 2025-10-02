package cn.nukkit.block;

public class BlockOreRedstoneDeepslateGlowing extends BlockOreRedstoneGlowing {
    @Override
    public String getName() {
        return "Lit Deepslate Redstone Ore";
    }

    @Override
    public int getId() {
        return LIT_DEEPSLATE_REDSTONE_ORE;
    }

    @Override
    public double getHardness() {
        return 4.5;
    }

    @Override
    protected int getTypeId() {
        return DEEPSLATE_REDSTONE_ORE;
    }
}
