package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemTool;
import cn.nukkit.utils.BlockColor;

public class BlockDoubleSlabCopperCutWaxed extends BlockDoubleSlabCopperCut {

    public BlockDoubleSlabCopperCutWaxed() {
        this(0);
    }

    public BlockDoubleSlabCopperCutWaxed(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return WAXED_DOUBLE_CUT_COPPER_SLAB;
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(WAXED_CUT_COPPER_SLAB), this.getDamage() & 0x07);
    }

    @Override
    public Item[] getDrops(Item item) {
        if (item.isPickaxe() && item.getTier() >= ItemTool.TIER_STONE) {
            Item slab = toItem();
            slab.setCount(2);
            return new Item[]{ slab };
        } else {
            return Item.EMPTY_ARRAY;
        }
    }

    @Override
    public boolean hasCopperBehavior() {
        return true;
    }

    @Override
    public int getCopperAge() {
        return 0;
    }

    @Override
    public int getDewaxedBlockId() {
        return DOUBLE_CUT_COPPER_SLAB;
    }

    @Override
    public int getIncrementAgeBlockId() {
        return WAXED_EXPOSED_DOUBLE_CUT_COPPER_SLAB;
    }
}
