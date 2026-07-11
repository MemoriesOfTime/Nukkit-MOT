package cn.nukkit.item.customitem;

import org.jetbrains.annotations.NotNull;

/**
 * 自定义盔甲的装备槽位。
 * <p>
 * The equipment slot of custom armor.
 */
public enum ArmorSlot {
    HEAD("slot.armor.head", "armor_head"),
    CHEST("slot.armor.chest", "armor_torso"),
    LEGS("slot.armor.legs", "armor_legs"),
    FEET("slot.armor.feet", "armor_feet");

    private final String wearableSlot;
    private final String enchantableSlot;

    ArmorSlot(@NotNull String wearableSlot, @NotNull String enchantableSlot) {
        this.wearableSlot = wearableSlot;
        this.enchantableSlot = enchantableSlot;
    }

    /**
     * 客户端 {@code minecraft:wearable.slot} 值。
     * <p>
     * The {@code minecraft:wearable.slot} value sent to the client.
     */
    public @NotNull String getWearableSlot() {
        return wearableSlot;
    }

    /**
     * 客户端 {@code item_properties.enchantable_slot} 值。
     * <p>
     * The {@code item_properties.enchantable_slot} value sent to the client.
     */
    public @NotNull String getEnchantableSlot() {
        return enchantableSlot;
    }
}
