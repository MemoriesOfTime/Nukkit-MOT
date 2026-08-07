package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemTool;
import cn.nukkit.utils.BlockColor;

public class BlockSlabDeepslateBrick extends BlockSlab {
    public BlockSlabDeepslateBrick() {
        this(0);
    }

    public BlockSlabDeepslateBrick(int meta) {
        super(meta, DEEPSLATE_BRICK_DOUBLE_SLAB);
    }

    @Override
    public int getId() {
        return DEEPSLATE_BRICK_SLAB;
    }

    @Override
    public String getName() {
        return "Deepslate Brick Slab";
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_PICKAXE;
    }

    @Override
    public double getResistance() {
        return 6;
    }

    @Override
    public double getHardness() {
        return 3.5;
    }

    @Override
    public Item[] getDrops(Item item) {
        if (item.isPickaxe() && item.getTier() >= ItemTool.TIER_WOODEN) {
            return new Item[]{
                    toItem()
            };
        } else {
            return Item.EMPTY_ARRAY;
        }
    }

    @Override
    public boolean canHarvestWithHand() {
        return false;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.DEEPSLATE_BLOCK_COLOR;
    }
}
