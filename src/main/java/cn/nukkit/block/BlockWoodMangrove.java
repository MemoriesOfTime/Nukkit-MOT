package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;

public class BlockWoodMangrove extends BlockWoodBark {

    public BlockWoodMangrove() {
        super(0);
    }

    public BlockWoodMangrove(int meta) {
        super(meta);
    }

    @Override
    public void setDamage(int meta) {
        super.setDamage(meta & ~0x7); // Clear the wood type bits
    }

    @Override
    public int getId() {
        return MANGROVE_WOOD;
    }

    @Override
    public String getName() {
        return "Mangrove Wood";
    }

    @Override
    protected int getStrippedId() {
        return STRIPPED_MANGROVE_WOOD;
    }

    @Override
    protected int getStrippedDamage() {
        return this.getDamage() & ~0x3;
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(MANGROVE_WOOD));
    }
}
