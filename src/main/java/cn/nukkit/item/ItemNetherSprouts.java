package cn.nukkit.item;

import cn.nukkit.block.Block;

public class ItemNetherSprouts extends Item {

    public ItemNetherSprouts() {
        this(0, 1);
    }

    public ItemNetherSprouts(Integer meta) {
        this(meta, 1);
    }

    public ItemNetherSprouts(Integer meta, int count) {
        super(NETHER_SPROUTS, 0, count, "Nether Sprouts");
        block = Block.get(NETHER_SPROUTS_BLOCK);
    }
}
