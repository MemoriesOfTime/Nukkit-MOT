
package cn.nukkit.block;

import cn.nukkit.item.Item;

/**
 * @author Justin
 */
public class BlockSkullWitherSkeleton extends BlockSkullSkeleton {

    public BlockSkullWitherSkeleton() {
        this(0);
    }

    public BlockSkullWitherSkeleton(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return WITHER_SKELETON_SKULL;
    }

    @Override
    public String getName() {
        return "Wither Skeleton Skull";
    }

    @Override
    public Item toItem() {
        return Item.get(Item.SKULL, 1);
    }
}
