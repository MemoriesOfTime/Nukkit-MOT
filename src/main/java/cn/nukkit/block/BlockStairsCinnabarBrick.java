package cn.nukkit.block;

public class BlockStairsCinnabarBrick extends BlockStairsCinnabar {
    public BlockStairsCinnabarBrick() {
        this(0);
    }

    public BlockStairsCinnabarBrick(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return CINNABAR_BRICK_STAIRS;
    }

    @Override
    public String getName() {
        return "Cinnabar Brick Stairs";
    }
}
