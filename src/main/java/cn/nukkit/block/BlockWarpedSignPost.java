package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemWarpedSign;

public class BlockWarpedSignPost extends BlockSignPost {

    public BlockWarpedSignPost() {
    }

    public BlockWarpedSignPost(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return WARPED_STANDING_SIGN;
    }

    @Override
    public int getWallId() {
        return WARPED_WALL_SIGN;
    }

    @Override
    public String getName() {
        return "Warped Sign Post";
    }

    @Override
    public Item toItem() {
        return new ItemWarpedSign();
    }
}
