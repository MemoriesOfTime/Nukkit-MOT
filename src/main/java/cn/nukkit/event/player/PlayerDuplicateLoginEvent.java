package cn.nukkit.event.player;

import cn.nukkit.Player;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;

public class PlayerDuplicateLoginEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    public static HandlerList getHandlers() {
        return handlers;
    }

    private final Player originalPlayer;

    public PlayerDuplicateLoginEvent(Player player, Player originalPlayer) {
        this.player = player;
        this.originalPlayer = originalPlayer;
    }

    public Player getOriginalPlayer() {
        return originalPlayer;
    }
}