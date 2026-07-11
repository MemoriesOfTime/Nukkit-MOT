package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockShelfWarped extends BlockShelf {

    public BlockShelfWarped() {
        this(0);
    }

    public BlockShelfWarped(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Warped Shelf";
    }

    @Override
    public int getId() {
        return WARPED_SHELF;
    }

    @Override
    public String getIdentifier() {
        return "minecraft:warped_shelf";
    }

    @Override
    public int getBurnChance() {
        return 0;
    }

    @Override
    public int getBurnAbility() {
        return 0;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.CYAN_BLOCK_COLOR;
    }
}
