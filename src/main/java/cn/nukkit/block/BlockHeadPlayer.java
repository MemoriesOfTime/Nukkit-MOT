
package cn.nukkit.block;

import cn.nukkit.item.Item;

/**
 * @author Justin
 */
public class BlockHeadPlayer extends BlockSkullSkeleton {

    public BlockHeadPlayer() {
        this(0);
    }

    public BlockHeadPlayer(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return PLAYER_HEAD;
    }

    @Override
    public String getName() {
        return "Player Head";
    }

    @Override
    public Item toItem() {
        return Item.get(Item.SKULL, 3);
    }
}
