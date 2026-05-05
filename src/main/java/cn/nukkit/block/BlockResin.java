package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockResin extends BlockSolid {

    @Override
    public int getId() {
        return RESIN_BLOCK;
    }

    @Override
    public String getName() {
        return "Block of Resin";
    }

    @Override
    public double getHardness() {
        return 0;
    }

    @Override
    public double getResistance() {
        return 0;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.ORANGE_TERRACOTA_BLOCK_COLOR;
    }
}
