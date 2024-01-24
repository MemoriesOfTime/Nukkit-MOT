package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;

public class BlockSoulLantern extends BlockLantern {
    @Override
    public int getId() {
        return SOUL_LANTERN;
    }

    @Override
    public String getName() {
        return "Soul Lantern";
    }

    @Override
    public int getLightLevel() {
        return 10;
    }

    @Override
    public Item toItem() {
        return super.toItem();
    }
}
