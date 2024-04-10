package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;

public class BlockWoodCherry extends BlockWoodBark {

    public BlockWoodCherry() {
        super(0);
    }

    public BlockWoodCherry(int meta) {
        super(meta);
    }

    @Override
    public void setDamage(int meta) {
        super.setDamage(meta & ~0x7); // Clear the wood type bits
    }

    @Override
    public int getId() {
        return CHERRY_WOOD;
    }

    @Override
    public String getName() {
        return "Cherry Wood";
    }

    @Override
    protected int getStrippedId() {
        return STRIPPED_CHERRY_WOOD;
    }

    @Override
    protected int getStrippedDamage() {
        return this.getDamage() & ~0x3;
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(CHERRY_WOOD));
    }
}