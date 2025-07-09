package cn.nukkit.block.news;


import cn.nukkit.block.BlockSolid;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.utils.BlockColor;

public abstract class BlockElement extends BlockSolid {

    protected BlockElement() {
        // meta has been removed in the latest version
    }

    @Override
    public double getHardness() {
        return 2;
    }

    @Override
    public double getResistance() {
        return 2;
    }

    @Override
    public Item[] getDrops(Item item) {
        if (item.isPickaxe() && item.getTier() >= this.getToolTier()) {
            if (item.hasEnchantment(Enchantment.ID_SILK_TOUCH)) {
                return new Item[]{this.toItem()};
            }
            return new Item[]{
                    Item.get(this.getDamage() == 0 ? Item.COBBLESTONE : Item.STONE, this.getDamage(), 1)
            };
        } else {
            return Item.EMPTY_ARRAY;
        }
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.BLACK_BLOCK_COLOR; //TODO: check ElementBlock::getMapColor -- 07/30/2022
    }


}