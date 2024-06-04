package cn.nukkit.block;

import cn.nukkit.item.Item;

public class BlockWallBrickBlackstonePolished extends BlockWallBlackstone {

    public BlockWallBrickBlackstonePolished() {
        this(0);
    }

    public BlockWallBrickBlackstonePolished(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Polished Blackstone Brick Wall";
    }

    @Override
    public int getId() {
        return POLISHED_BLACKSTONE_BRICK_WALL;
    }
}
