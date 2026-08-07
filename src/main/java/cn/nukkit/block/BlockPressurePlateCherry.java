package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockPressurePlateCherry extends BlockPressurePlateWood {
    public BlockPressurePlateCherry() {
        this(0);
    }

    public BlockPressurePlateCherry(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return CHERRY_PRESSURE_PLATE;
    }

    @Override
    public String getName() {
        return "Cherry Pressure Plate";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.CHERRY_BLOCK_COLOR;
    }
}
