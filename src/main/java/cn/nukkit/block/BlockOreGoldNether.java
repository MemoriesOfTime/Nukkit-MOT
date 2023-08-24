package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;
import cn.nukkit.item.ItemTool;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.math.NukkitRandom;

public class BlockOreGoldNether extends BlockOreGold {
    @Override
    public int getId() {
        return NETHER_GOLD_ORE;
    }

    @Override
    public int getToolTier() {
        return ItemTool.TIER_WOODEN;
    }

    @Override
    public String getName() {
        return "Nether Gold Ore";
    }

    @Override
    public Item[] getDrops(final Item item) {
        if (!item.isPickaxe() || item.getTier() < ItemTool.TIER_WOODEN) {
            return Item.EMPTY_ARRAY;
        }

        final Enchantment enchantment = item.getEnchantment(Enchantment.ID_FORTUNE_DIGGING);
        int fortune = 0;
        if (enchantment != null) {
            fortune = enchantment.getLevel();
        }

        final NukkitRandom nukkitRandom = new NukkitRandom();
        int count = nukkitRandom.nextRange(2, 6);
        switch (fortune) {
            case 0 -> {
            }
            case 1 -> {
                if (nukkitRandom.nextRange(0, 2) == 0) {
                    count *= 2;
                }
            }
            case 2 -> {
                if (nukkitRandom.nextRange(0, 1) == 0) {
                    count *= nukkitRandom.nextRange(2, 3);
                }
            }
            case 3 -> {
                if (nukkitRandom.nextRange(0, 4) < 3) {
                    count *= nukkitRandom.nextRange(2, 4);
                }
            }
        }

        return new Item[]{Item.get(ItemID.GOLD_NUGGET, 0, count)};
    }
}
