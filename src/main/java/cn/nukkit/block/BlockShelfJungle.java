package cn.nukkit.block;

public class BlockShelfJungle extends BlockShelf {

    public BlockShelfJungle() {
        this(0);
    }

    public BlockShelfJungle(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Jungle Shelf";
    }

    @Override
    public int getId() {
        return JUNGLE_SHELF;
    }

    @Override
    public String getIdentifier() {
        return "minecraft:jungle_shelf";
    }
}
