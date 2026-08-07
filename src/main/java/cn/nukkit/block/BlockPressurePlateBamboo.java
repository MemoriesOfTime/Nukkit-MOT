package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockPressurePlateBamboo extends BlockPressurePlateWood {
    public BlockPressurePlateBamboo() {
        this(0);
    }

    public BlockPressurePlateBamboo(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return BAMBOO_PRESSURE_PLATE;
    }

    @Override
    public String getName() {
        return "Bamboo Pressure Plate";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.BAMBOO_BLOCK_COLOR;
    }
}