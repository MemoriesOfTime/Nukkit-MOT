package cn.nukkit.registry;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Registries {
    public static final BlockEntityRegistry BLOCK_ENTITY = new BlockEntityRegistry();
    public static final EntityRegistry ENTITY = new EntityRegistry();
}
