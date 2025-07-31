package cn.nukkit.block;

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

    @Override
    public String getIdentifier() {
        return "minecraft:deepslate_tile_wall";
    }
}
