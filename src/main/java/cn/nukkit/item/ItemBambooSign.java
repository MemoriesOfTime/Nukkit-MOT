package cn.nukkit.item;

import cn.nukkit.block.BlockBambooSignPost;

public class ItemBambooSign extends ItemSign {

    public ItemBambooSign() {
        this(0, 1);
    }

    public ItemBambooSign(Integer meta) {
        this(meta, 1);
    }

    public ItemBambooSign(Integer meta, int count) {
        super(BAMBOO_SIGN, meta, count, "Bamboo Sign", new BlockBambooSignPost());
    }
}

