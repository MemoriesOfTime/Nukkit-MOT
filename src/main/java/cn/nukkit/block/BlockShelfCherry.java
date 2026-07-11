package cn.nukkit.block;

public class BlockShelfCherry extends BlockShelf {

    public BlockShelfCherry() {
        this(0);
    }

    public BlockShelfCherry(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Cherry Shelf";
    }

    @Override
    public int getId() {
        return CHERRY_SHELF;
    }

    @Override
    public String getIdentifier() {
        return "minecraft:cherry_shelf";
    }
}
