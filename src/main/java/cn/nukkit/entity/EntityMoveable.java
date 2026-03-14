package cn.nukkit.entity;

import org.jetbrains.annotations.Nullable;

/**
 * Ported from PowerNukkitX
 */
public interface EntityMoveable {

    String PROPERTY_STATE = "minecraft:can_move";

    @Nullable
    default Boolean isMoveable() {
        if (this instanceof Entity entity) {
            return entity.getBooleanEntityProperty(PROPERTY_STATE);
        }
        return null;
    }

    default void setMoveable(Boolean value) {
        if (this instanceof Entity entity) {
            entity.setBooleanEntityProperty(PROPERTY_STATE, value);
        }
    }
}