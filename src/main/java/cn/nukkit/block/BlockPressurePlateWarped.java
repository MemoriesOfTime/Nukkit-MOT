package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockPressurePlateWarped extends BlockPressurePlateWood {
    public BlockPressurePlateWarped() {
        this(0);
    }

    public BlockPressurePlateWarped(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return WARPED_PRESSURE_PLATE;
    }

    @Override
    public String getName() {
        return "Warped Pressure Plate";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.CYAN_BLOCK_COLOR;
    }
}
