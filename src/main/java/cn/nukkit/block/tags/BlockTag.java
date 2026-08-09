package cn.nukkit.block.tags;

import it.unimi.dsi.fastutil.ints.IntSet;

public interface BlockTag {

    IntSet getBlockId();

    default boolean has(int blockId) {
        return getBlockId().contains(blockId);
    }

    static BlockTag of(int... blockId) {
        return new SimpleBlockTag(blockId);
    }
}
