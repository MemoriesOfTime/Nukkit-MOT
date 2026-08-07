package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.utils.BlockColor;

public class BlockWoodPaleOak extends BlockWoodBark {

    public BlockWoodPaleOak() {
        super(0);
    }

    public BlockWoodPaleOak(int meta) {
        super(meta);
    }

    @Override
    public void setDamage(int meta) {
        super.setDamage(meta & ~0x7);
    }

    @Override
    public int getId() {
        return PALE_OAK_WOOD;
    }

    @Override
    public String getName() {
        return "Pale Oak Wood";
    }

    @Override
    protected int getStrippedId() {
        return STRIPPED_PALE_OAK_WOOD;
    }

    @Override
    protected int getStrippedDamage() {
        return this.getDamage() & ~0x7;
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(PALE_OAK_WOOD));
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.STONE_BLOCK_COLOR;
    }
}
