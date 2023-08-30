package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemWarpedSign;

public class BlockWarpedWallSign extends BlockWallSign {

    public BlockWarpedWallSign() {
        this(0);
    }

    public BlockWarpedWallSign(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return WARPED_WALL_SIGN;
    }

    @Override
    protected int getPostId() {
        return WARPED_STANDING_SIGN;
    }

    @Override
    public String getName() {
        return "Warped Wall Sign";
    }

    @Override
    public Item toItem() {
        return new ItemWarpedSign();
    }
}
