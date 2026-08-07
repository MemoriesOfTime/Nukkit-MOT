package cn.nukkit.block;

import cn.nukkit.item.ItemTool;
import cn.nukkit.utils.BlockColor;

public class BlockHeavyCore extends BlockTransparent {

    public BlockHeavyCore() {
    }

    @Override
    public int getId() {
        return HEAVY_CORE;
    }

    @Override
    public String getName() {
        return "Heavy Core";
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_PICKAXE;
    }

    @Override
    public double getHardness() {
        return 3;
    }

    @Override
    public double getResistance() {
        return 15;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.METAL_BLOCK_COLOR;
    }

    @Override
    public boolean isSolid() {
        return false;
    }

    @Override
    public double getMinX() {
        return this.y + 4 / 16.0;
    }

    @Override
    public double getMinZ() {
        return this.y + 4 / 16.0;
    }

    @Override
    public double getMaxX() {
        return this.y + 1 - 4 / 16.0;
    }

    @Override
    public double getMaxY() {
        return this.y + 1 - 8 / 16.0;
    }

    @Override
    public double getMaxZ() {
        return this.y + 1 - 4 / 16.0;
    }

}