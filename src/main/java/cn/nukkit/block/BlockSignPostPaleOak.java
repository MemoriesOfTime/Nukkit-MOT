package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemSignPaleOak;
import cn.nukkit.utils.BlockColor;

public class BlockSignPostPaleOak extends BlockSignPost {

    public BlockSignPostPaleOak() {
        this(0);
    }

    public BlockSignPostPaleOak(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return PALE_OAK_STANDING_SIGN;
    }

    @Override
    public int getWallId() {
        return PALE_OAK_WALL_SIGN;
    }

    @Override
    public String getName() {
        return "Pale Oak Sign Post";
    }

    @Override
    public Item toItem() {
        return new ItemSignPaleOak();
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.QUARTZ_BLOCK_COLOR;
    }
}
