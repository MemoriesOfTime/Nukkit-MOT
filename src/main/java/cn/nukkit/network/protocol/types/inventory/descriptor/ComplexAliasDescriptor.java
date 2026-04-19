package cn.nukkit.network.protocol.types.inventory.descriptor;

import lombok.Value;

@Value
public class ComplexAliasDescriptor implements ItemDescriptor {
    String name;

    @Override
    public ItemDescriptorType getType() {
        return ItemDescriptorType.COMPLEX_ALIAS;
    }
}
