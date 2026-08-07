package cn.nukkit.block;

public class BlockWallSulfurBrick extends BlockWallSulfur {
    public BlockWallSulfurBrick() {
        this(0);
    }

    public BlockWallSulfurBrick(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Sulfur Brick Wall";
    }

    @Override
    public int getId() {
        return SULFUR_BRICK_WALL;
    }

    @Override
    public String getIdentifier() {
        return "minecraft:sulfur_brick_wall";
    }
}
