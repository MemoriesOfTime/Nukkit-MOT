package cn.nukkit.item.material.tags;

import cn.nukkit.item.material.ItemType;
import cn.nukkit.item.material.tags.impl.SimpleItemTag;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.util.Arrays;
import java.util.Set;

public interface ItemTag {

    Set<ItemType> getItemTypes();

    default boolean has(ItemType itemType) {
        return this.getItemTypes().contains(itemType);
    }

    default ItemTag copyWith(ItemType... itemTypes) {
        Set<ItemType> newItemTypes = new ObjectOpenHashSet<>(this.getItemTypes());
        newItemTypes.addAll(Arrays.asList(itemTypes));
        return new SimpleItemTag(newItemTypes);
    }

    static ItemTag of(ItemType... itemTypes) {
        return new SimpleItemTag(itemTypes);
    }
}
