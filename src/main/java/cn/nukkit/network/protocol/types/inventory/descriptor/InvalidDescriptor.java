package cn.nukkit.network.protocol.types.inventory.descriptor;

public final class InvalidDescriptor implements ItemDescriptor {

    public static final InvalidDescriptor INSTANCE = new InvalidDescriptor();

    private InvalidDescriptor() {
    }

    @Override
    public ItemDescriptorType getType() {
        return ItemDescriptorType.INVALID;
    }
}
