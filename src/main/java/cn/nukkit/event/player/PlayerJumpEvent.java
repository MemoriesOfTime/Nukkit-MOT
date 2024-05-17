package cn.nukkit.event.player;

import cn.nukkit.Player;
import cn.nukkit.event.HandlerList;

public class PlayerJumpEvent extends PlayerEvent {

    private static final HandlerList handlers = new HandlerList();

    public PlayerJumpEvent(Player player) {
        this.player = player;
    }

    public static HandlerList getHandlers() {
        return handlers;
    }
}
