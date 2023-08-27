package cn.nukkit.item.enchantment.protection;

import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityDamageEvent.DamageCause;
import cn.nukkit.item.enchantment.Enchantment;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class EnchantmentProtectionAll extends EnchantmentProtection {

    public EnchantmentProtectionAll() {
        super(Enchantment.ID_PROTECTION_ALL, "all", Rarity.COMMON, TYPE.ALL, 20);
    }

    @Override
    public int getMinEnchantingPower(int level) {
        return 11 * (level - 1) + 1;
    }

    @Override
    public int getMinEnchantAbility(int level) {
        return 1 + (level - 1) * 11;
    }

    @Override
    public int getMaxEnchantAbility(int level) {
        return this.getMinEnchantAbility(level) + 20;
    }

    @Override
    public double getTypeModifier() {
        return 1;
    }

    @Override
    public float getProtectionFactor(EntityDamageEvent e) {
        DamageCause cause = e.getCause();

        if (level <= 0 || cause == DamageCause.VOID || cause == DamageCause.CUSTOM || cause == DamageCause.MAGIC || cause == DamageCause.HUNGER) {
            return 0;
        }

        return (float) (getLevel() * getTypeModifier());
    }
}
