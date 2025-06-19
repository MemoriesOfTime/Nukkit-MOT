
package cn.nukkit.block;

import cn.nukkit.item.Item;

/**
 * @author Justin
 */
public class BlockHeadPiglin extends BlockSkullSkeleton {

    public BlockHeadPiglin() {
        this(0);
    }

    public BlockHeadPiglin(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return PIGLIN_HEAD;
    }

    @Override
    public String getName() {
        return "Piglin Head";
    }

    @Override
    public Item toItem() {
        return Item.get(Item.SKULL, 6);
    }
}
