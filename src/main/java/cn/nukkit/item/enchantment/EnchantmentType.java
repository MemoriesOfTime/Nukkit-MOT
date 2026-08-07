package cn.nukkit.item.enchantment;

import cn.nukkit.item.*;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public enum EnchantmentType {
    ALL,
    ARMOR,
    ARMOR_HEAD,
    ARMOR_TORSO,
    ARMOR_LEGS,
    ARMOR_FEET,
    SWORD,
    DIGGER,
    FISHING_ROD,
    BREAKABLE,
    BOW,
    WEARABLE,
    TRIDENT,
    CROSSBOW,
    MACE,
    SPEAR;

    public boolean canEnchantItem(Item item) {
        if (this == ALL) {
            return true;

        } else if (this == BREAKABLE && item.getMaxDurability() >= 0) {
            return true;

        } else if (item instanceof ItemBook) {
            return true;

        } else if (item instanceof ItemArmor) {
            if (this == WEARABLE || (this == ARMOR && item.isArmor())) {
                return true;
            }

            return switch (this) {
                case ARMOR_HEAD -> item.isHelmet();
                case ARMOR_TORSO -> item.isChestplate();
                case ARMOR_LEGS -> item.isLeggings();
                case ARMOR_FEET -> item.isBoots();
                default -> false;
            };
        } else {
            return switch (this) {
                case SWORD -> item.isSword();
                case DIGGER -> item.isPickaxe() || item.isShovel() || item.isAxe() || item.isHoe();
                case BOW -> item instanceof ItemBow;
                case FISHING_ROD -> item instanceof ItemFishingRod;
                case WEARABLE -> item instanceof ItemSkull;
                case TRIDENT -> item instanceof ItemTrident;
                case CROSSBOW -> item instanceof ItemCrossbow;
                case MACE -> item instanceof ItemMace;
                case SPEAR -> item.isSpear();
                default -> false;
            };
        }
    }
}
