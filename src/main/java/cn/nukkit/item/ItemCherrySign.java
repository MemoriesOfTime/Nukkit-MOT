package cn.nukkit.item;

import cn.nukkit.block.BlockCherrySignPost;

public class ItemCherrySign extends ItemSign {

    public ItemCherrySign() {
        this(0, 1);
    }

    public ItemCherrySign(Integer meta) {
        this(meta, 1);
    }

    public ItemCherrySign(Integer meta, int count) {
        super(CHERRY_SIGN, meta, count, "Cherry Sign", new BlockCherrySignPost());
    }
}
