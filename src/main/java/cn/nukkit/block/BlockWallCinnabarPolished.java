package cn.nukkit.block;

public class BlockWallCinnabarPolished extends BlockWallCinnabar {
    public BlockWallCinnabarPolished() {
        this(0);
    }

    public BlockWallCinnabarPolished(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Polished Cinnabar Wall";
    }

    @Override
    public int getId() {
        return POLISHED_CINNABAR_WALL;
    }

    @Override
    public String getIdentifier() {
        return "minecraft:polished_cinnabar_wall";
    }
}
