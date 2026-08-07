
package cn.nukkit.block;

import cn.nukkit.item.Item;

/**
 * @author Justin
 */
public class BlockHeadCreeper extends BlockSkullSkeleton {

    public BlockHeadCreeper() {
        this(0);
    }

    public BlockHeadCreeper(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return CREEPER_HEAD;
    }

    @Override
    public String getName() {
        return "Creeper Head";
    }

    @Override
    public Item toItem() {
        return Item.get(Item.SKULL, 4);
    }

    @Override
    public SkullType getSkullType() {
        return SkullType.CREEPER;
    }
}
