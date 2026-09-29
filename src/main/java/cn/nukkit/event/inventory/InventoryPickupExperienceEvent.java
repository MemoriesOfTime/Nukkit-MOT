package cn.nukkit.event.inventory;

import cn.nukkit.entity.item.EntityXPOrb;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;
import cn.nukkit.inventory.Inventory;

/** Called before consuming an orb or changing equipment through Mending. */
public class InventoryPickupExperienceEvent extends InventoryEvent implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private final EntityXPOrb experienceOrb;

    public static HandlerList getHandlers() {
        return handlers;
    }

    public InventoryPickupExperienceEvent(Inventory inventory, EntityXPOrb experienceOrb) {
        super(inventory);
        this.experienceOrb = experienceOrb;
    }

    public EntityXPOrb getExperienceOrb() {
        return experienceOrb;
    }
}
