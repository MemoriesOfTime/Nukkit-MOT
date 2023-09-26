package cn.nukkit.item.enchantment.damage;

import cn.nukkit.entity.Entity;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class EnchantmentDamageAll extends EnchantmentDamage {

    public EnchantmentDamageAll() {
        super(ID_DAMAGE_ALL, "all", Rarity.COMMON, TYPE.ALL, 20);
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
    public int getMaxEnchantableLevel() {
        return 4;
    }

    @Override
    public double getDamageBonus(Entity entity) {
        return this.getLevel() * 1.25;
    }
}
