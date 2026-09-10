package cn.nukkit.block;

import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.SimpleAxisAlignedBB;

import cn.nukkit.item.ItemTool;

public class BlockStonecutter extends BlockSolid {

    @Override
    public int getId() {
        return STONECUTTER;
    }

    @Override
    public double getHardness() {
        return 3.5;
    }

    @Override
    protected AxisAlignedBB recalculateBoundingBox() {
        return new SimpleAxisAlignedBB(
                this.x, this.y, this.z, this.x + 1, this.y + 9d / 16d, this.z + 1);
    }

    @Override
    public double getResistance() {
        return 17.5;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_PICKAXE;
    }

    @Override
    public int getToolTier() {
        return ItemTool.TIER_WOODEN;
    }

    @Override
    public String getName() {
        return "Stonecutter";
    }

    @Override
    public boolean canHarvestWithHand() {
        return false;
    }

    @Override
    public WaterloggingType getWaterloggingType() {
        return WaterloggingType.WHEN_PLACED_IN_WATER;
    }
}
