package cn.nukkit.block;

import cn.nukkit.BlockWallDeepslateCobbled;

public class BlockWallDeepslateBrick extends BlockWallDeepslateCobbled {
    public BlockWallDeepslateBrick() {
        this(0);
    }

    public BlockWallDeepslateBrick(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Deepslate Brick Wall";
    }

    @Override
    public int getId() {
        return DEEPSLATE_BRICK_WALL;
    }
}
