package cn.nukkit.network.protocol.types.inventory.descriptor;

import lombok.Value;

@Value
public class ItemTagDescriptor implements ItemDescriptor {
    String itemTag;

    @Override
    public ItemDescriptorType getType() {
        return ItemDescriptorType.ITEM_TAG;
    }
}
