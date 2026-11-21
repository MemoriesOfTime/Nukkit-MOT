package cn.nukkit.item;

import cn.nukkit.block.BlockSignPostPoplar;

public class ItemSignPoplar extends ItemSign {

    public ItemSignPoplar() {
        this(0, 1);
    }

    public ItemSignPoplar(Integer meta) {
        this(meta, 1);
    }

    public ItemSignPoplar(Integer meta, int count) {
        super(POPLAR_SIGN, meta, count, "Poplar Sign", new BlockSignPostPoplar());
    }
}
