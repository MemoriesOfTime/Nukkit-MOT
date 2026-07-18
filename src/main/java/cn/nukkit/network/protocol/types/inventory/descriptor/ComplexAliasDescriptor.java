package cn.nukkit.network.protocol.types.inventory.descriptor;

import cn.nukkit.inventory.ItemTag;
import cn.nukkit.item.Item;
import lombok.Value;

@Value
public class ComplexAliasDescriptor implements ItemDescriptor {
    String name;

    @Override
    public ItemDescriptorType getType() {
        return ItemDescriptorType.COMPLEX_ALIAS;
    }

    @Override
    public boolean match(Item item) {
        return item != null
                && !item.isNull()
                && ItemTag.getItemSet(name).contains(item.getNamespaceId());
    }
}
