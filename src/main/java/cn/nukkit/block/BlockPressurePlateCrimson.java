package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockPressurePlateCrimson extends BlockPressurePlateWood {
    public BlockPressurePlateCrimson() {
        this(0);
    }

    public BlockPressurePlateCrimson(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return CRIMSON_PRESSURE_PLATE;
    }

    @Override
    public String getName() {
        return "Crimson Pressure Plate";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.NETHER_BLOCK_COLOR;
    }
}
