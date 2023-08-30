package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemJungleSign;

public class BlockJungleSignPost extends BlockSignPost {

    public BlockJungleSignPost() {
    }

    public BlockJungleSignPost(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return JUNGLE_STANDING_SIGN;
    }

    @Override
    public int getWallId() {
        return JUNGLE_WALL_SIGN;
    }

    @Override
    public String getName() {
        return "Jungle Sign Post";
    }

    @Override
    public Item toItem() {
        return new ItemJungleSign();
    }

}
