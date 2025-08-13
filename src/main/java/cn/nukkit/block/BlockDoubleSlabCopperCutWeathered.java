package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.utils.BlockColor;

public class BlockDoubleSlabCopperCutWeathered extends BlockDoubleSlabCopperCut {

    public BlockDoubleSlabCopperCutWeathered() {
        this(0);
    }

    public BlockDoubleSlabCopperCutWeathered(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return WEATHERED_DOUBLE_CUT_COPPER_SLAB;
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(WEATHERED_CUT_COPPER_SLAB));
    }

    @Override
    public Item[] getDrops(Item item) {
        return new Item[]{
                new ItemBlock(Block.get(WEATHERED_CUT_COPPER_SLAB), 0, 2)
        };
    }

    @Override
    public boolean hasCopperBehavior() {
        return true;
    }

    @Override
    public int getCopperAge() {
        return 2;
    }

    @Override
    public int getWaxedBlockId() {
        return WAXED_WEATHERED_DOUBLE_CUT_COPPER_SLAB;
    }

    @Override
    public int getIncrementAgeBlockId() {
        return OXIDIZED_DOUBLE_CUT_COPPER_SLAB;
    }

    @Override
    public int getDecrementAgeBlockId() {
        return EXPOSED_DOUBLE_CUT_COPPER_SLAB;
    }
}
