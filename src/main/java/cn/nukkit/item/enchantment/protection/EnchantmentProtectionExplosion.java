package cn.nukkit.item.enchantment.protection;

import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityDamageEvent.DamageCause;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class EnchantmentProtectionExplosion extends EnchantmentProtection {

    public EnchantmentProtectionExplosion() {
        super(ID_PROTECTION_EXPLOSION, "explosion", Rarity.RARE, TYPE.EXPLOSION, 12);
    }

    @Override
    public int getMinEnchantingPower(int level) {
        return 8 * (level - 1) + 5;
    }

    @Override
    public int getMinEnchantAbility(int level) {
        return 5 + ((level - 1) << 3);
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

        if (level <= 0 || (cause != DamageCause.ENTITY_EXPLOSION && cause != DamageCause.BLOCK_EXPLOSION)) {
            return 0;
        }

        return (float) (getLevel() * getTypeModifier());
    }
}
