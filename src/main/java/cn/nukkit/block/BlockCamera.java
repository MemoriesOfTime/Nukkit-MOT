package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockCamera extends BlockSolid {

    public BlockCamera() {
    }

    @Override
    public int getId() {
        return CAMERA_BLOCK;
    }

    @Override
    public String getName() {
        return "Camera";
    }

    @Override
    public double getHardness() {
        return 0;
    }

    @Override
    public double getResistance() {
        return 0;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.WOOD_BLOCK_COLOR;
    }
}
