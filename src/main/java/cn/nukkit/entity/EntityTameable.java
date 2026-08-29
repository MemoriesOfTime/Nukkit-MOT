package cn.nukkit.entity;

import cn.nukkit.Player;
import cn.nukkit.item.Item;

public interface EntityTameable {

    String NAMED_TAG_OWNER = "Owner";

    String NAMED_TAG_OWNER_UUID = "OwnerUUID";

    String NAMED_TAG_SITTING = "Sitting";

    Player getOwner();

    boolean hasOwner();

    void setOwner(Player player);

    String getOwnerUUID();

    void setOwnerUUID(String uuid);

    boolean isSitting();

    void setSitting(boolean sitting);

    /**
     * Does using this item on this entity tame it.
     *
     * <p>The item lives inside {@code onInteract} of every tameable entity, so a server can only
     * learn that a wild animal has just become a pet after it already happened. Asking here lets
     * it decide while the item is still in the player's hand, the same way
     * {@code BaseEntity#isBreedingItem} does for breeding. Whether the entity is free to be tamed
     * at all - it has no owner, it is not angry - stays with the caller.
     */
    default boolean isTamingItem(Item item) {
        return false;
    }

    default boolean isOwner(Entity entity) {
        return entity instanceof Player && entity.getUniqueId().toString().equals(this.getOwnerUUID());
    }
}
