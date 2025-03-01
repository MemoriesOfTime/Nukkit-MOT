package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemCherrySign;
import cn.nukkit.utils.BlockColor;

public class BlockCherrySignPost extends BlockSignPost {
    public BlockCherrySignPost() {

    }

    public BlockCherrySignPost(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return CHERRY_STANDING_SIGN;
    }

    @Override
    public int getWallId() {
        return CHERRY_WALL_SIGN;
    }

    @Override
    public String getName() {
        return "Cherry Sign Post";
    }

    @Override
    public Item toItem() {
        return new ItemCherrySign();
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.CHERRY_BLOCK_COLOR;
    }
}
