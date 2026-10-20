package cn.nukkit.event.player;

import cn.nukkit.Player;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;
import cn.nukkit.item.ItemSpear;

/**
 * A spear's Lunge impulse, after charged damage and before motion, sound and exhaustion.
 * Cancelling this event leaves both the charged attack and the ordinary jab intact.
 * The dedicated handler list does not deliver a second generic motion notification.
 */
public final class PlayerSpearLungeEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    public static HandlerList getHandlers() {
        return handlers;
    }

    private final ItemSpear spear;

    public PlayerSpearLungeEvent(Player player, ItemSpear spear) {
        this.player = player;
        this.spear = spear;
    }

    public ItemSpear getSpear() {
        return this.spear;
    }
}
