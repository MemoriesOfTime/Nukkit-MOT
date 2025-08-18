package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemTool;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.math.BlockFace;
import cn.nukkit.utils.BlockColor;

public class BlockDeepslate extends BlockSolidMeta {

    private static final short[] faces = new short[]{
            0,
            0,
            2,
            2,
            1,
            1
    };

    public BlockDeepslate() {
        this(0);
    }

    protected BlockDeepslate(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Deepslate";
    }

    @Override
    public int getId() {
        return DEEPSLATE;
    }

    @Override
    public double getHardness() {
        return 3;
    }

    @Override
    public double getResistance() {
        return 6;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_PICKAXE;
    }

    @Override
    public int getToolTier() {
        return ItemTool.TIER_WOODEN;
    }

    @Override
    public boolean canHarvestWithHand() {
        return false;
    }

    @Override
    public Item[] getDrops(Item item) {
        if (!canHarvest(item)) {
            return Item.EMPTY_ARRAY;
        }
        if (item.hasEnchantment(Enchantment.ID_SILK_TOUCH)) {
                return new Item[]{this.toItem()};
        }
        return new Item[]{new ItemBlock(Block.get(BlockID.COBBLED_DEEPSLATE))};
    }
    
    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(this.getId(), 0), 0);
    }

    @Override
    public boolean canSilkTouch() {
        return true;
    }

    @Override
    public boolean place(Item item, Block block, Block target, BlockFace face, double fx, double fy, double fz, Player player) {
        this.setDamage(faces[face.getIndex()]);
        this.getLevel().setBlock(block, this, true, true);
        return true;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.DEEPSLATE_BLOCK_COLOR;
    }
}