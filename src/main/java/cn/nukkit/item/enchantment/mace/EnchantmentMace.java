package cn.nukkit.item.enchantment.mace;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.item.enchantment.EnchantmentType;

public abstract class EnchantmentMace extends Enchantment {

    protected EnchantmentMace(int id, String identifier, Rarity rarity) {
        super(id, identifier, rarity, EnchantmentType.MACE);
    }

    @Override
    public boolean canEnchant(Item item) {
        return item.getNamespaceId().equals("minecraft:mace") || item.getId() == ItemID.ENCHANTED_BOOK;
    }
}