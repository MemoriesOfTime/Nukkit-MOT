package cn.nukkit.item;

import cn.nukkit.block.BlockCampfireSoul;

public class ItemCampfireSoul extends Item {
    public ItemCampfireSoul() {
        this(0, 1);
    }

    public ItemCampfireSoul(final Integer meta) {
        this(meta, 1);
    }

    public ItemCampfireSoul(final Integer meta, final int count) {
        super(SOUL_CAMPFIRE, meta, count, "Soul Campfire");
        block = new BlockCampfireSoul();
    }
}
