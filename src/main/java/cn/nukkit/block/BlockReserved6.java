package cn.nukkit.block;

/**
 * Created by PetteriM1
 */
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemDiamond;
import cn.nukkit.item.ItemIngotIron;
import cn.nukkit.item.ItemTool;
import cn.nukkit.item.enchantment.Enchantment;

public class BlockReserved6 extends BlockSolid {

    @Override
    public int getId() {
        return RESERVED6;
    }

    @Override
    public String getName() {
        return "Nether Reactor Cores";
    }

    @Override
    public double getHardness() {
        return 1.5;
    }

    @Override
    public double getResistance() {
        return 30;
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
    public boolean canHarvestWithHand() {
        return false;
    }

    @Override
    public boolean canSilkTouch() {
        return true;
    }
}
