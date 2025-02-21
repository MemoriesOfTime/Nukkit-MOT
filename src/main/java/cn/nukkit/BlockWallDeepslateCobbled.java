package cn.nukkit;

import cn.nukkit.block.BlockWall;
import cn.nukkit.item.ItemTool;
import cn.nukkit.utils.BlockColor;

public class BlockWallDeepslateCobbled extends BlockWall {
    public BlockWallDeepslateCobbled() {
        this(0);
    }

    public BlockWallDeepslateCobbled(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Cobbled Deepslate Wall";
    }

    @Override
    public int getId() {
        return COBBLED_DEEPSLATE_WALL;
    }

    @Override
    public double getHardness() {
        return 3.5;
    }

    @Override
    public double getResistance() {
        return 6;
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
        return BlockColor.DEEPSLATE_BLOCK_COLOR;
    }
}
