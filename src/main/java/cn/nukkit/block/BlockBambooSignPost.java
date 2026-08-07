package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBambooSign;

public class BlockBambooSignPost extends BlockSignPost {

    public BlockBambooSignPost() {
    }

    public BlockBambooSignPost(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return BAMBOO_STANDING_SIGN;
    }

    @Override
    public int getWallId() {
        return BAMBOO_WALL_SIGN;
    }

    @Override
    public String getName() {
        return "Bamboo Sign Post";
    }

    @Override
    public Item toItem() {
        return new ItemBambooSign();
    }
}
