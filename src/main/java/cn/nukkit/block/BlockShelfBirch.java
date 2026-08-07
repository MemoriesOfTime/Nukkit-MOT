package cn.nukkit.block;

public class BlockShelfBirch extends BlockShelf {

    public BlockShelfBirch() {
        this(0);
    }

    public BlockShelfBirch(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Birch Shelf";
    }

    @Override
    public int getId() {
        return BIRCH_SHELF;
    }

    @Override
    public String getIdentifier() {
        return "minecraft:birch_shelf";
    }
}
