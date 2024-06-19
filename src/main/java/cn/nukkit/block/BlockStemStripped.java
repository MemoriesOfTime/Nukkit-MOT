package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.item.Item;

public abstract class BlockStemStripped extends BlockStem {

    public BlockStemStripped() {
        super();
    }

    public BlockStemStripped(int meta) {
        super(meta);
    }

    @Override
    public boolean canBeActivated() {
        return false;
    }

    @Override
    public boolean onActivate(Item item, Player player) {
        return false;
    }

    @Override
    public int getStrippedId() {
        return this.getId();
    }
}
