package cn.nukkit.event.player;

import cn.nukkit.Player;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;
import lombok.Getter;

@Getter
public class PlayerDuplicateLoginEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final Player originalPlayer;

    public PlayerDuplicateLoginEvent(Player player, Player originalPlayer) {
        this.player = player;
        this.originalPlayer = originalPlayer;
    }
}
