package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockButtonBlackstonePolished extends BlockButtonStone {
    public BlockButtonBlackstonePolished() {
        this(0);
    }

    public BlockButtonBlackstonePolished(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return POLISHED_BLACKSTONE_BUTTON;
    }

    @Override
    public String getName() {
        return "Polished Blackstone Button";
    }
    
    @Override
    public BlockColor getColor() {
        return BlockColor.BLACK_BLOCK_COLOR;
    }
}
