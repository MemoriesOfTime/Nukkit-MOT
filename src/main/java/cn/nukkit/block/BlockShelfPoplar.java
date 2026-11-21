package cn.nukkit.block;

public class BlockShelfPoplar extends BlockShelf {

    public BlockShelfPoplar() {
        this(0);
    }

    public BlockShelfPoplar(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Poplar Shelf";
    }

    @Override
    public int getId() {
        return POPLAR_SHELF;
    }

    @Override
    public String getIdentifier() {
        return "minecraft:poplar_shelf";
    }
}
