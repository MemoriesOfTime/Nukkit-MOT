package cn.nukkit.block;

public class BlockShelfSpruce extends BlockShelf {

    public BlockShelfSpruce() {
        this(0);
    }

    public BlockShelfSpruce(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Spruce Shelf";
    }

    @Override
    public int getId() {
        return SPRUCE_SHELF;
    }

    @Override
    public String getIdentifier() {
        return "minecraft:spruce_shelf";
    }
}
