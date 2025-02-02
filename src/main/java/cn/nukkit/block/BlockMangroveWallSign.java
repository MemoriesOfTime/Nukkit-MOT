package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemCrimsonSign;
import cn.nukkit.utils.BlockColor;

public class BlockMangroveWallSign extends BlockWallSign {

    public BlockMangroveWallSign() {
        this(0);
    }

    public BlockMangroveWallSign(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return MANGROVE_WALL_SIGN;
    }

    @Override
    protected int getPostId() {
        return MANGROVE_STANDING_SIGN;
    }

    @Override
    public String getName() {
        return "Mangrove Wall Sign";
    }

    @Override
    public Item toItem() {
        return new ItemCrimsonSign();
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.MANGROVE_BLOCK_COLOR;
    }
}

