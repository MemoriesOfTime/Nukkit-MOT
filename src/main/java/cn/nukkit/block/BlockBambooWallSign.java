package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBambooSign;

public class BlockBambooWallSign extends BlockWallSign {

    public BlockBambooWallSign() {
        this(0);
    }

    public BlockBambooWallSign(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return BAMBOO_WALL_SIGN;
    }

    @Override
    protected int getPostId() {
        return BAMBOO_STANDING_SIGN;
    }

    @Override
    public String getName() {
        return "Bamboo Wall Sign";
    }

    @Override
    public Item toItem() {
        return new ItemBambooSign();
    }
}
