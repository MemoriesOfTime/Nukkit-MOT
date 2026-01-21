package cn.nukkit.block;

public class BlockWallTuffPolished extends BlockWallTuff {
    public BlockWallTuffPolished() {
        this(0);
    }

    public BlockWallTuffPolished(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Polished Tuff Wall";
    }

    @Override
    public int getId() {
        return POLISHED_TUFF_WALL;
    }

    @Override
    public String getIdentifier() {
        return "minecraft:polished_tuff_wall";
    }
}
