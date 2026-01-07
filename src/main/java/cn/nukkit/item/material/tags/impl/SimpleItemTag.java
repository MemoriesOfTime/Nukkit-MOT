package cn.nukkit.item.material.tags.impl;

import cn.nukkit.item.material.ItemType;
import cn.nukkit.item.material.tags.ItemTag;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import lombok.Data;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

@Data
public class SimpleItemTag implements ItemTag {

    private final Set<ItemType> itemTypes;

    public SimpleItemTag(ItemType... itemTypes) {
        this(Arrays.asList(itemTypes));
    }

    public SimpleItemTag(Collection<ItemType> itemTypes) {
        this.itemTypes = Collections.unmodifiableSet(new ObjectOpenHashSet<>(itemTypes));
    }
}
