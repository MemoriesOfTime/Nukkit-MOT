package cn.nukkit.block;

import cn.nukkit.BlockWallDeepslateCobbled;

public class BlockWallDeepslateTile extends BlockWallDeepslateCobbled {
    public BlockWallDeepslateTile() {
        this(0);
    }

    public BlockWallDeepslateTile(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Deepslate Tile Wall";
    }

    @Override
    public int getId() {
        return DEEPSLATE_TILE_WALL;
    }
}
