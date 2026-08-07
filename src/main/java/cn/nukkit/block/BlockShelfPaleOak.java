package cn.nukkit.block;

public class BlockShelfPaleOak extends BlockShelf {

    public BlockShelfPaleOak() {
        this(0);
    }

    public BlockShelfPaleOak(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Pale Oak Shelf";
    }

    @Override
    public int getId() {
        return PALE_OAK_SHELF;
    }

    @Override
    public String getIdentifier() {
        return "minecraft:pale_oak_shelf";
    }
}
