package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemTool;
import cn.nukkit.utils.BlockColor;

/**
 * Created by CreeperFace on 26. 11. 2016.
 */
public class BlockSlabRedSandstone extends BlockSlab {

    public static final int RED_SANDSTONE = 0;
    public static final int PURPUR = 1;
    public static final int PRISMARINE = 2;
    public static final int PRISMARINE_BRICKS = 3;
    public static final int DARK_PRISMARINE = 4;

    public BlockSlabRedSandstone() {
        this(RED_SANDSTONE);
    }

    public BlockSlabRedSandstone(int meta) {
        super(meta, DOUBLE_RED_SANDSTONE_SLAB);
    }

    @Override
    public int getId() {
        return RED_SANDSTONE_SLAB;
    }

    @Override
    public String getName() {
        String[] names = new String[]{
                "Red Sandstone",
                "Purpur",
                "",
                "",
                "",
                "",
                "",
                ""
        };

        return ((this.getDamage() & 0x08) > 0 ? "Upper " : "") + names[this.getDamage() & 0x07] + " Slab";
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_PICKAXE;
    }

    @Override
    public double getHardness() {
        switch (this.getDamage() & 0x07) {
            case PRISMARINE:
            case PRISMARINE_BRICKS:
            case DARK_PRISMARINE:
                return 1.5;
            default:
                return 2;
        }
    }

    @Override
    public int getToolTier() {
        return ItemTool.TIER_WOODEN;
    }

    @Override
    public Item toItem() {
        return new ItemBlock(this, this.getDamage() & 0x07);
    }

    @Override
    public boolean canHarvestWithHand() {
        return false;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.ORANGE_BLOCK_COLOR;
    }
}
