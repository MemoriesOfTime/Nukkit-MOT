package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.utils.BlockColor;

public class BlockWoodPoplar extends BlockWoodBark {

    public BlockWoodPoplar() {
        super(0);
    }

    public BlockWoodPoplar(int meta) {
        super(meta);
    }

    @Override
    public void setDamage(int meta) {
        super.setDamage(meta & ~0x7);
    }

    @Override
    public int getId() {
        return POPLAR_WOOD;
    }

    @Override
    public String getName() {
        return "Poplar Wood";
    }

    @Override
    protected int getStrippedId() {
        return STRIPPED_POPLAR_WOOD;
    }

    @Override
    protected int getStrippedDamage() {
        return this.getDamage() & ~0x7;
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(POPLAR_WOOD));
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.SPRUCE_BLOCK_COLOR;
    }
}
