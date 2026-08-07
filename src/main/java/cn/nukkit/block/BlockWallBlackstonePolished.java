package cn.nukkit.block;

public class BlockWallBlackstonePolished extends BlockWallBlackstone {

    public BlockWallBlackstonePolished() {
        this(0);
    }

    public BlockWallBlackstonePolished(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Polished Blackstone Wall";
    }

    @Override
    public int getId() {
        return POLISHED_BLACKSTONE_WALL;
    }

    @Override
    public String getIdentifier() {
        return "minecraft:polished_blackstone_wall";
    }
}
