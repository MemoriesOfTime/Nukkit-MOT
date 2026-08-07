package cn.nukkit.network.protocol.types.inventory.descriptor;

import lombok.Value;

@Value
public class MolangDescriptor implements ItemDescriptor {
    String expression;
    int version;

    @Override
    public ItemDescriptorType getType() {
        return ItemDescriptorType.MOLANG;
    }
}
