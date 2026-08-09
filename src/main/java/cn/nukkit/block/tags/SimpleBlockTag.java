package cn.nukkit.block.tags;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import lombok.Data;

@Data
public class SimpleBlockTag implements BlockTag {

    private final IntSet blockId;

    public SimpleBlockTag(int... blockId) {
        this.blockId = new IntOpenHashSet(blockId);
    }
}
