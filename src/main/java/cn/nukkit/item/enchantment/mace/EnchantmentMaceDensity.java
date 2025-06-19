package cn.nukkit.item.enchantment.mace;

import cn.nukkit.entity.Entity;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.item.enchantment.damage.EnchantmentDamage;

public class EnchantmentMaceDensity extends EnchantmentMace {

    public EnchantmentMaceDensity() {
        super(Enchantment.ID_DENSITY, "heavy_weapon.density", Rarity.UNCOMMON);
    }

    @Override
    public int getMaxLevel() {
        return 5;
    }

    @Override
    public int getMinEnchantAbility(int level) {
        return 8 * level - 3;
    }

    @Override
    public int getMaxEnchantAbility(int level) {
        return 8 * level + 17;
    }

    @Override
    public double getDamageBonus(Entity entity, Entity attacker) {
        double fallDistance = attacker.highestPosition - entity.y;
        if (fallDistance <= 0) {
            return 0;
        }
        return getLevel() * 0.5d * fallDistance;
    }

    @Override
    public boolean checkCompatibility(Enchantment enchantment) {
        return super.checkCompatibility(enchantment) && enchantment.id != Enchantment.ID_DENSITY && !(enchantment instanceof EnchantmentDamage);
    }
}