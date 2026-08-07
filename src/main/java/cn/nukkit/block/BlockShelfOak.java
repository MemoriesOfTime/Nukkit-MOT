package cn.nukkit.block;

public class BlockShelfOak extends BlockShelf {

    public BlockShelfOak() {
        this(0);
    }

    public BlockShelfOak(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Oak Shelf";
    }

    @Override
    public int getId() {
        return OAK_SHELF;
    }

    @Override
    public String getIdentifier() {
        return "minecraft:oak_shelf";
    }
}
