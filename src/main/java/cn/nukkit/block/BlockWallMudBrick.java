package cn.nukkit.block;

import cn.nukkit.item.ItemTool;
import cn.nukkit.utils.BlockColor;

public class BlockWallMudBrick extends BlockWall {

    public BlockWallMudBrick() {
        this(0);
    }

    public BlockWallMudBrick(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Mud Brick Wall";
    }

    @Override
    public int getId() {
        return MUD_BRICK_WALL;
    }

    @Override
    public double getHardness() {
        return 1.5;
    }

    @Override
    public double getResistance() {
        return 3;
    }

    @Override
    public boolean canHarvestWithHand() {
        return false;
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
    public BlockColor getColor() {
        return BlockColor.BROWN_BLOCK_COLOR;
    }
}