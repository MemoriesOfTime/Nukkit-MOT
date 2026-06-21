package cn.nukkit.item.enchantment.loot;

import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.item.enchantment.EnchantmentType;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class EnchantmentLootWeapon extends EnchantmentLoot {

    public EnchantmentLootWeapon() {
        super(Enchantment.ID_LOOTING, "lootBonus", Rarity.RARE, EnchantmentType.SWORD);
    }

    @Override
    public boolean canEnchant(Item item) {
        return item.isSpear() || super.canEnchant(item);
    }
}
