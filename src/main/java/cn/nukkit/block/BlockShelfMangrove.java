package cn.nukkit.block;

public class BlockShelfMangrove extends BlockShelf {

    public BlockShelfMangrove() {
        this(0);
    }

    public BlockShelfMangrove(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Mangrove Shelf";
    }

    @Override
    public int getId() {
        return MANGROVE_SHELF;
    }

    @Override
    public String getIdentifier() {
        return "minecraft:mangrove_shelf";
    }
}
