package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockLogStrippedCherry extends BlockWoodStripped {

    public BlockLogStrippedCherry() {
        super(0);
    }

    public BlockLogStrippedCherry(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return STRIPPED_CHERRY_LOG;
    }

    @Override
    public String getName() {
        return "Stripped Cherry Log";
    }

    @Override
    public double getResistance() {
        return 2;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.WHITE_TERRACOTA_BLOCK_COLOR;
    }
}