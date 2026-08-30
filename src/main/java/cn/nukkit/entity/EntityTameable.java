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
     * 判断该物品是否为本实体的驯服物品；是否满足驯服前提（无主人、未愤怒等）由调用方判断。
     * <p>
     * Whether using this item tames this entity. Taming preconditions (no owner, not angry)
     * are up to the caller.
     */
    default boolean isTamingItem(Item item) {
        return false;
    }

    default boolean isOwner(Entity entity) {
        return entity instanceof Player && entity.getUniqueId().toString().equals(this.getOwnerUUID());
    }
}
