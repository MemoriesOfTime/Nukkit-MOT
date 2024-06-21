package cn.nukkit.block;


import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.utils.BlockColor;

public class BlockCopperBulb extends BlockCopperBulbBase {

    public BlockCopperBulb() {
        this(0);
    }

    public BlockCopperBulb(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Copper Bulb";
    }

    @Override
    public int getId() {
        return COPPER_BULB;
    }

    @Override
    public Item[] getDrops(Item item) {
        return new Item[]{
                new ItemBlock(Block.get(this.getId()))
        };
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.ADOBE_BLOCK_COLOR;
    }

    @Override
    public int getLightLevel() {
        return this.isLit() ? 15 : 0;
    }
}