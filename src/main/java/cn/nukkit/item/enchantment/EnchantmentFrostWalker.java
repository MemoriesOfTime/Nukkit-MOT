package cn.nukkit.item.enchantment;

public class EnchantmentFrostWalker extends Enchantment {

    /**
     * 原版冻结半径上限：clamp(2 + level, 0, 16)
     * <p>
     * Vanilla cap on the freeze radius: clamp(2 + level, 0, 16)
     */
    public static final int MAX_FREEZE_RADIUS = 16;

    protected EnchantmentFrostWalker() {
        super(ID_FROST_WALKER, "frostwalker", Rarity.RARE, EnchantmentType.ARMOR_FEET);
    }

    @Override
    public int getMinEnchantAbility(int level) {
        return level * 10;
    }

    @Override
    public int getMaxEnchantAbility(int level) {
        return this.getMinEnchantAbility(level) + 15;
    }

    @Override
    public int getMaxLevel() {
        return 2;
    }

    @Override
    public boolean isTreasure() {
        return true;
    }

    @Override
    public boolean checkCompatibility(Enchantment enchantment) {
        return super.checkCompatibility(enchantment) && enchantment.id != ID_WATER_WALKER;
    }
}
