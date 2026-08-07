package cn.nukkit.network.protocol.types.inventory.descriptor;

import lombok.Value;

@Value
public class ItemDescriptorWithCount {
    ItemDescriptor descriptor;
    int count;

    public static ItemDescriptorWithCount empty() {
        return new ItemDescriptorWithCount(InvalidDescriptor.INSTANCE, 0);
    }
}
