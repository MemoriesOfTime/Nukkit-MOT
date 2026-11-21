package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemSignPoplar;
import cn.nukkit.utils.BlockColor;

public class BlockWallSignPoplar extends BlockWallSign {

    public BlockWallSignPoplar() {
        this(0);
    }

    public BlockWallSignPoplar(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return POPLAR_WALL_SIGN;
    }

    @Override
    protected int getPostId() {
        return POPLAR_STANDING_SIGN;
    }

    @Override
    public String getName() {
        return "Poplar Wall Sign";
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
