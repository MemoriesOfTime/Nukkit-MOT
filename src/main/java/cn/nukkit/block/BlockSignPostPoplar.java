package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemSignPoplar;
import cn.nukkit.utils.BlockColor;

public class BlockSignPostPoplar extends BlockSignPost {

    public BlockSignPostPoplar() {
        this(0);
    }

    public BlockSignPostPoplar(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return POPLAR_STANDING_SIGN;
    }

    @Override
    public int getWallId() {
        return POPLAR_WALL_SIGN;
    }

    @Override
    public String getName() {
        return "Poplar Sign Post";
    }

    @Override
    public Item toItem() {
        return new ItemSignPoplar();
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.SPRUCE_BLOCK_COLOR;
    }
}
