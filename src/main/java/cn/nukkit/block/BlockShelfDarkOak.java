package cn.nukkit.block;

public class BlockShelfDarkOak extends BlockShelf {

    public BlockShelfDarkOak() {
        this(0);
    }

    public BlockShelfDarkOak(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Dark Oak Shelf";
    }

    @Override
    public int getId() {
        return DARK_OAK_SHELF;
    }

    @Override
    public String getIdentifier() {
        return "minecraft:dark_oak_shelf";
    }
}
