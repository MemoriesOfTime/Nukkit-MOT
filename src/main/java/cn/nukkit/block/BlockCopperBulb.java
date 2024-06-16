package cn.nukkit.block;


import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.utils.BlockColor;

public class BlockCopperBulb extends BlockCopperBulbBase {

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
        return BlockColor.AIR_BLOCK_COLOR;
    }
}