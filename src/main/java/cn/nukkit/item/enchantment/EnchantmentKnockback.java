package cn.nukkit.item.enchantment;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class EnchantmentKnockback extends Enchantment {

    protected EnchantmentKnockback() {
        super(ID_KNOCKBACK, "knockback", Rarity.UNCOMMON, EnchantmentType.SWORD, 50);
    }

    @Override
    public int getMinEnchantingPower(int level) {
        return 20 * (level - 1) + 5;
    }

    @Override
    public int getMinEnchantAbility(int level) {
        return 5 + (level - 1) * 20;
    }

    @Override
    public int getMaxEnchantAbility(int level) {
        return super.getMinEnchantAbility(level) + 50;
    }

    @Override
    public int getMaxLevel() {
        return 2;
    }
}
