package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockPressurePlatePaleOak extends BlockPressurePlateWood {

    public BlockPressurePlatePaleOak() {
        this(0);
    }

    public BlockPressurePlatePaleOak(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return PALE_OAK_PRESSURE_PLATE;
    }

    @Override
    public String getName() {
        return "Pale Oak Pressure Plate";
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.QUARTZ_BLOCK_COLOR;
    }
}
