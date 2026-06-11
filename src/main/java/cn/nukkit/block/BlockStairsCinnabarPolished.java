package cn.nukkit.block;

public class BlockStairsCinnabarPolished extends BlockStairsCinnabar {
    public BlockStairsCinnabarPolished() {
        this(0);
    }

    public BlockStairsCinnabarPolished(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return POLISHED_CINNABAR_STAIRS;
    }

    @Override
    public String getName() {
        return "Polished Cinnabar Stairs";
    }
}
