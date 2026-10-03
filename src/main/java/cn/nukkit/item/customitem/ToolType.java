package cn.nukkit.item.customitem;

import org.jetbrains.annotations.Nullable;

/**
 * 自定义工具的类型。
 * <p>
 * The type of custom tool.
 */
public enum ToolType {
    PICKAXE("minecraft:is_pickaxe", "pickaxe"),
    AXE("minecraft:is_axe", "axe"),
    SHOVEL("minecraft:is_shovel", "shovel"),
    HOE("minecraft:is_hoe", "hoe"),
    SWORD(null, "sword"),
    SHEARS("minecraft:is_shears", null);

    @Nullable
    private final String itemTag;
    @Nullable
    private final String enchantableSlot;

    ToolType(@Nullable String itemTag, @Nullable String enchantableSlot) {
        this.itemTag = itemTag;
        this.enchantableSlot = enchantableSlot;
    }

    /**
     * 写入 {@code item_tags} 的标签（用于服务端判定 {@code isPickaxe()} 等及客户端工具类型）。
     * {@code null} 表示该工具类型无 item_tag（如剑）。
     * <p>
     * The tag written into {@code item_tags}. {@code null} means the tool type
     * has no item tag (e.g. sword).
     */
    public @Nullable String getItemTag() {
        return itemTag;
    }

    /**
     * 客户端 {@code item_properties.enchantable_slot} 值。{@code null} 表示无附魔槽位。
     * <p>
     * The {@code item_properties.enchantable_slot} value sent to the client.
     * {@code null} means no enchantable slot.
     */
    public @Nullable String getEnchantableSlot() {
        return enchantableSlot;
    }
}
