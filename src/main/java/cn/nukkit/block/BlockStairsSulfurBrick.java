package cn.nukkit.block;

public class BlockStairsSulfurBrick extends BlockStairsSulfur {
    public BlockStairsSulfurBrick() {
        this(0);
    }

    public BlockStairsSulfurBrick(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return SULFUR_BRICK_STAIRS;
    }

    @Override
    public String getName() {
        return "Sulfur Brick Stairs";
    }
}
