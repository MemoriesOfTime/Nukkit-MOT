package cn.nukkit.block;

public class BlockShelfAcacia extends BlockShelf {

    public BlockShelfAcacia() {
        this(0);
    }

    public BlockShelfAcacia(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Acacia Shelf";
    }

    @Override
    public int getId() {
        return ACACIA_SHELF;
    }

    @Override
    public String getIdentifier() {
        return "minecraft:acacia_shelf";
    }
}
