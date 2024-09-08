package cn.nukkit.block;

import cn.nukkit.item.Item;

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
}
