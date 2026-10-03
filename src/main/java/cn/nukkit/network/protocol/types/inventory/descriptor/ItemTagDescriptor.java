package cn.nukkit.network.protocol.types.inventory.descriptor;

import cn.nukkit.inventory.ItemTag;
import cn.nukkit.item.Item;
import lombok.Value;

@Value
public class ItemTagDescriptor implements ItemDescriptor {
    String itemTag;

    @Override
    public ItemDescriptorType getType() {
        return ItemDescriptorType.ITEM_TAG;
    }

    @Override
    public boolean match(Item item) {
        return item != null
                && !item.isNull()
                && ItemTag.getItemSet(itemTag).contains(item.getNamespaceId());
    }
}
