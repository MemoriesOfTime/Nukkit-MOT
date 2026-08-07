package cn.nukkit.network.protocol.types.inventory.descriptor;

import cn.nukkit.item.Item;

/**
 * Ingredient descriptor used in Auto-craft requests and server-authoritative
 * recipe payloads. Replaces the legacy {@code List<Item>} ingredient format
 * with version-aware descriptor types.
 * <p>
 * Adapted from PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>)
 */
public interface ItemDescriptor {

    ItemDescriptorType getType();

    /**
     * Returns an example {@link Item} representative of this descriptor. Used
     * primarily for populating event payloads; may return an AIR item when a
     * concrete representation is unavailable (MOLANG/INVALID descriptors).
     */
    default Item toItem() {
        return Item.get(Item.AIR);
    }

    /**
     * Returns {@code true} when the given item satisfies this descriptor. Tag
     * descriptors that the server cannot resolve default to {@code false}.
     */
    default boolean match(Item item) {
        return false;
    }
}
