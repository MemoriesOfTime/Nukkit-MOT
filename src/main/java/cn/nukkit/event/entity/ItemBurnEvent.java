package cn.nukkit.event.entity;

import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.event.HandlerList;

/**
 * Called right before an item entity standing in a fire block is removed.
 *
 * <p>This is the only way an item entity leaves the world without a pickup, a despawn event or
 * a damage event: the fire branch of {@link EntityItem#onUpdate(int)} closes it directly. The
 * event is informational and cannot be cancelled, so the vanilla behaviour stays exactly the
 * same; listeners only learn which stack burned.
 */
public class ItemBurnEvent extends EntityEvent {
    private static final HandlerList handlers = new HandlerList();

    public static HandlerList getHandlers() {
        return handlers;
    }

    public ItemBurnEvent(EntityItem item) {
        this.entity = item;
    }

    @Override
    public EntityItem getEntity() {
        return (EntityItem) this.entity;
    }
}
