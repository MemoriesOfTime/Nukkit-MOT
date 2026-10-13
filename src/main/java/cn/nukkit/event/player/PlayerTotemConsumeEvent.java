package cn.nukkit.event.player;

import cn.nukkit.Player;
import cn.nukkit.event.HandlerList;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.item.Item;

/** Completed native rescue with a confirmed decrement of the selected totem. Not cancellable. */
public final class PlayerTotemConsumeEvent extends PlayerEvent {
    private static final HandlerList handlers = new HandlerList();
    private final Item item;
    private final EntityDamageEvent damageEvent;
    private final int slot;
    private final boolean offhand;

    public static HandlerList getHandlers() { return handlers; }

    public PlayerTotemConsumeEvent(Player player, Item item, EntityDamageEvent damageEvent,
                                   int slot, boolean offhand) {
        this.player = player;
        this.item = item.clone();
        this.damageEvent = damageEvent;
        this.slot = slot;
        this.offhand = offhand;
    }

    public Item getItem() { return item.clone(); }
    public EntityDamageEvent getDamageEvent() { return damageEvent; }
    public int getSlot() { return slot; }
    public boolean isOffhand() { return offhand; }
}
