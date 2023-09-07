package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemSpruceSign;

public class BlockSpruceSignPost extends BlockSignPost {

    public BlockSpruceSignPost() {
    }

    public BlockSpruceSignPost(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return SPRUCE_STANDING_SIGN;
    }

    @Override
    public int getWallId() {
        return SPRUCE_WALL_SIGN;
    }

    @Override
    public String getName() {
        return "Spruce Sign Post";
    }

    @Override
    public Item toItem() {
        return new ItemSpruceSign();
    }
}
