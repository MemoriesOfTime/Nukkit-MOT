package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.utils.BlockColor;

public class BlockDoubleSlabCopperCutExposed extends BlockDoubleSlabCopperCut {

    public BlockDoubleSlabCopperCutExposed() {
        this(0);
    }

    public BlockDoubleSlabCopperCutExposed(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return EXPOSED_DOUBLE_CUT_COPPER_SLAB;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.LIGHT_GRAY_TERRACOTA_BLOCK_COLOR;
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(EXPOSED_CUT_COPPER_SLAB));
    }

    @Override
    public Item[] getDrops(Item item) {
        return new Item[]{
                new ItemBlock(Block.get(EXPOSED_CUT_COPPER_SLAB), 0, 2)
        };
    }

    @Override
    public boolean hasCopperBehavior() {
        return true;
    }

    @Override
    public int getCopperAge() {
        return 1;
    }

    @Override
    public int getWaxedBlockId() {
        return WAXED_EXPOSED_DOUBLE_CUT_COPPER_SLAB;
    }

    @Override
    public int getIncrementAgeBlockId() {
        return WEATHERED_DOUBLE_CUT_COPPER_SLAB;
    }

    @Override
    public int getDecrementAgeBlockId() {
        return DOUBLE_CUT_COPPER_SLAB;
    }
}
