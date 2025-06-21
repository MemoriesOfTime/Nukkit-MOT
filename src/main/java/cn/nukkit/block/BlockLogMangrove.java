package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.utils.BlockColor;

public class BlockLogMangrove extends BlockWood {

    public BlockLogMangrove() {
        this(0);
    }

    public BlockLogMangrove(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Mangrove Log";
    }

    @Override
    public int getId() {
        return MANGROVE_LOG;
    }

    @Override
    protected int getStrippedId() {
        return STRIPPED_MANGROVE_LOG;
    }

    @Override
    protected int getStrippedDamage() {
        return this.getDamage() & ~0x3;
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(this.getId(), 0), 0);
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.RED_BLOCK_COLOR;
    }
}