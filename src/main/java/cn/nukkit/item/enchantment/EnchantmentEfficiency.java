package cn.nukkit.item.enchantment;

import cn.nukkit.item.Item;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class EnchantmentEfficiency extends Enchantment {

    protected EnchantmentEfficiency() {
        super(ID_EFFICIENCY, "digging", Rarity.COMMON, EnchantmentType.DIGGER, 50);
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
        return super.getMinEnchantAbility(level) + 50;
    }

    @Override
    public int getMaxLevel() {
        return 5;
    }

    @Override
    public boolean canEnchant(Item item) {
        return item.isShears() || super.canEnchant(item);
    }
}
