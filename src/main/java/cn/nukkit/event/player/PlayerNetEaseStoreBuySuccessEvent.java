package cn.nukkit.event.player;

import cn.nukkit.Player;
import cn.nukkit.api.OnlyNetEase;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;

/**
 * NetEase store purchase success notification carried by PyRpcPacket.
 */
@OnlyNetEase
public class PlayerNetEaseStoreBuySuccessEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    public PlayerNetEaseStoreBuySuccessEvent(Player player) {
        this.player = player;
    }

    public static HandlerList getHandlers() {
        return handlers;
    }
}
