package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockPressurePlatePoplar extends BlockPressurePlateWood {

    public BlockPressurePlatePoplar() {
        this(0);
    }

    public BlockPressurePlatePoplar(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return POPLAR_PRESSURE_PLATE;
    }

    @Override
    public String getName() {
        return "Poplar Pressure Plate";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.SPRUCE_BLOCK_COLOR;
    }
}
