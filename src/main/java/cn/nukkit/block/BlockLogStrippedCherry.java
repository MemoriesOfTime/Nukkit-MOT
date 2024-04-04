package cn.nukkit.block;

public class BlockLogStrippedCherry extends BlockWoodStripped {

    public BlockLogStrippedCherry() {
        super(0);
    }

    public BlockLogStrippedCherry(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return STRIPPED_CHERRY_LOG;
    }

    @Override
    public String getName() {
        return "Stripped Cherry Log";
    }

    @Override
    public double getResistance() {
        return 2;
    }
}