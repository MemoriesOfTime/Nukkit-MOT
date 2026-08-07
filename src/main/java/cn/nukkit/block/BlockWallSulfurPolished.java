package cn.nukkit.block;

public class BlockWallSulfurPolished extends BlockWallSulfur {
    public BlockWallSulfurPolished() {
        this(0);
    }

    public BlockWallSulfurPolished(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Polished Sulfur Wall";
    }

    @Override
    public int getId() {
        return POLISHED_SULFUR_WALL;
    }

    @Override
    public String getIdentifier() {
        return "minecraft:polished_sulfur_wall";
    }
}
