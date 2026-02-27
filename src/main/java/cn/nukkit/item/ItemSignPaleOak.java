package cn.nukkit.item;

import cn.nukkit.block.BlockSignPostPaleOak;

public class ItemSignPaleOak extends ItemSign {

    public ItemSignPaleOak() {
        this(0, 1);
    }

    public ItemSignPaleOak(Integer meta) {
        this(meta, 1);
    }

    public ItemSignPaleOak(Integer meta, int count) {
        super(PALE_OAK_SIGN, meta, count, "Pale Oak Sign", new BlockSignPostPaleOak());
    }
}
