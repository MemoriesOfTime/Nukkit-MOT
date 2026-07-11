package cn.nukkit.network.protocol.types.inventory.descriptor;

import cn.nukkit.item.Item;
import lombok.Value;

@Value
public class DeferredDescriptor implements ItemDescriptor {
    String name;
    int auxValue;

    @Override
    public ItemDescriptorType getType() {
        return ItemDescriptorType.DEFERRED;
    }

    @Override
    public Item toItem() {
        try {
            return Item.fromString(name);
        } catch (Exception ignored) {
            return Item.get(Item.AIR);
        }
    }

    @Override
    public boolean match(Item item) {
        if (item == null || item.isNull()) {
            return false;
        }
        Item expected = toItem();
        if (expected.isNull()) {
            return false;
        }
        if (expected.getId() != item.getId()) {
            return false;
        }
        return auxValue == Short.MAX_VALUE || item.getDamage() == auxValue;
    }
}
