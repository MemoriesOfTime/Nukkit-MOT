package cn.nukkit.item;

import cn.nukkit.block.BlockCampfire;

public class ItemCampfire extends Item {
    public ItemCampfire() {
        this(0, 1);
    }

    public ItemCampfire(final Integer meta) {
        this(meta, 1);
    }

    public ItemCampfire(final Integer meta, final int count) {
        super(CAMPFIRE, meta, count, "Campfire");
        block = new BlockCampfire();
    }
}
