package cn.nukkit.item.enchantment.protection;

import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityDamageEvent.DamageCause;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class EnchantmentProtectionFire extends EnchantmentProtection {

    public EnchantmentProtectionFire() {
        super(ID_PROTECTION_FIRE, "fire", Rarity.UNCOMMON, TYPE.FIRE, 12);
    }

    @Override
    public int getMinEnchantingPower(int level) {
        return 8 * (level - 1) + 10;
    }

    @Override
    public int getMinEnchantAbility(int level) {
        return 10 + (level - 1 << 3);
    }

    @Override
    public int getMaxEnchantAbility(int level) {
        return this.getMinEnchantAbility(level) + 12;
    }

    @Override
    public double getTypeModifier() {
        return 2;
    }

    @Override
    public float getProtectionFactor(EntityDamageEvent e) {
        DamageCause cause = e.getCause();

        if (level <= 0 || (cause != DamageCause.LAVA && cause != DamageCause.FIRE && cause != DamageCause.FIRE_TICK)) {
            return 0;
        }

        return (float) (getLevel() * getTypeModifier());
    }
}
