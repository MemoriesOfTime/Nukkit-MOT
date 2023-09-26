package cn.nukkit.item.enchantment;

/**
 * @author Rover656
 */
public class EnchantmentMending extends Enchantment {

    protected EnchantmentMending() {
        super(ID_MENDING, "mending", Rarity.RARE, EnchantmentType.BREAKABLE, 50);
    }

    @Override
    public int getMinEnchantingPower(int level) {
        return 25;
    }

    @Override
    public int getMinEnchantAbility(int level) {
        return 25 * level;
    }

    @Override
    public int getMaxEnchantAbility(int level) {
        return this.getMinEnchantAbility(level) + 50;
    }

    @Override
    public boolean isTreasure() {
        return true;
    }

    @Override
    public boolean checkCompatibility(Enchantment enchantment) {
        return super.checkCompatibility(enchantment) && enchantment.id != ID_BOW_INFINITY;
    }
}