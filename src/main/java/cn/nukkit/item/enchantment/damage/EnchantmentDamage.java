package cn.nukkit.item.enchantment.damage;

import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.item.enchantment.EnchantmentType;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public abstract class EnchantmentDamage extends Enchantment {

    protected EnchantmentDamage(int id, String name, Rarity rarity, TYPE type) {
        super(id, name, rarity, EnchantmentType.SWORD);
    }

    @Override
    public boolean checkCompatibility(Enchantment enchantment) {
        return !(enchantment instanceof EnchantmentDamage);
    }

    @Override
    public boolean canEnchant(Item item) {
        return item.isAxe() || super.canEnchant(item);
    }

    @Override
    public int getMaxLevel() {
        return 5;
    }

    @Override
    public String getName() {
        return "%enchantment.damage." + this.name;
    }

    @Override
    public boolean isMajor() {
        return true;
    }

    public enum TYPE {
        ALL,
        SMITE,
        ARTHROPODS
    }
}
