package cn.nukkit.item.enchantment.bow;

import cn.nukkit.item.enchantment.Enchantment;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class EnchantmentBowPower extends EnchantmentBow {

    public EnchantmentBowPower() {
        super(Enchantment.ID_BOW_POWER, "arrowDamage", Rarity.COMMON, 15);
    }

    @Override
    public int getMinEnchantingPower(int level) {
        return 10 * (level - 1) + 1;
    }

    @Override
    public int getMinEnchantAbility(int level) {
        return 1 + (level - 1) * 10;
    }

    @Override
    public int getMaxEnchantAbility(int level) {
        return this.getMinEnchantAbility(level) + 15;
    }

    @Override
    public int getMaxLevel() {
        return 5;
    }
}
