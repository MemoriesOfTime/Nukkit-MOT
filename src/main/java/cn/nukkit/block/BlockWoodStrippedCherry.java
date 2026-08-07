package cn.nukkit.block;

import cn.nukkit.utils.BlockColor;

public class BlockWoodStrippedCherry extends BlockWoodStripped {

    public BlockWoodStrippedCherry() {
        super(0);
    }

    public BlockWoodStrippedCherry(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return STRIPPED_CHERRY_WOOD;
    }

    @Override
    public String getName() {
        return "Stripped Cherry Wood";
    }

    @Override
    public int getBurnAbility() {
        return 5;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.WHITE_TERRACOTA_BLOCK_COLOR;
    }
}
