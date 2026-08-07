package cn.nukkit.block;

public class BlockStairsSulfurPolished extends BlockStairsSulfur {
    public BlockStairsSulfurPolished() {
        this(0);
    }

    public BlockStairsSulfurPolished(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return POLISHED_SULFUR_STAIRS;
    }

    @Override
    public String getName() {
        return "Polished Sulfur Stairs";
    }
}
