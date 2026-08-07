package cn.nukkit.network.protocol.types.inventory.descriptor;

import cn.nukkit.item.Item;
import lombok.Value;

@Value
public class DefaultDescriptor implements ItemDescriptor {
    int itemId;
    int auxValue;

    @Override
    public ItemDescriptorType getType() {
        return ItemDescriptorType.DEFAULT;
    }

    @Override
    public Item toItem() {
        return Item.get(itemId, auxValue);
    }

    @Override
    public boolean match(Item item) {
        if (item == null || item.isNull()) {
            return false;
        }
        if (item.getId() != itemId) {
            return false;
        }
        return auxValue == Short.MAX_VALUE || item.getDamage() == auxValue;
    }
}
