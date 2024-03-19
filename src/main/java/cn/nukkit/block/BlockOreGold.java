package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemRawGold;
import cn.nukkit.item.ItemTool;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.Utils;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class BlockOreGold extends BlockSolid {

    @Override
    public int getId() {
        return GOLD_ORE;
    }

    @Override
    public double getHardness() {
        return 3;
    }

    @Override
    public double getResistance() {
        return 15;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_PICKAXE;
    }

    @Override
    public int getToolTier() {
        return ItemTool.TIER_IRON;
    }

    @Override
    public String getName() {
        return "Gold Ore";
    }

    @Override
    public boolean canHarvestWithHand() {
        return false;
    }

    @Override
    public boolean canDropRaw(Player player) {
        return player.protocol >= ProtocolInfo.v1_17_0;
    }

    @Override
    public Item[] getDrops(Item item) {
        if (item.isPickaxe() && item.getTier() >= this.getToolTier()) {
            if (item.hasEnchantment(Enchantment.ID_SILK_TOUCH)) {
                return new Item[]{this.toItem()};
            }
            int count = 1;
            Enchantment fortune = item.getEnchantment(Enchantment.ID_FORTUNE_DIGGING);
            if (fortune != null && fortune.getLevel() >= 1) {
                int i = Utils.random.nextInt(fortune.getLevel() + 2) - 1;

                if (i < 0) {
                    i = 0;
                }

                count = i + 1;
            }

            Item rawGold = new ItemRawGold();
            rawGold.setCount(count);
            return new Item[]{
                    rawGold
            };
        } else {
            return Item.EMPTY_ARRAY;
        }
    }
}
