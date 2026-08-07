package cn.nukkit.event.player;

import cn.nukkit.Player;
import cn.nukkit.api.OnlyNetEase;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;
import cn.nukkit.network.protocol.netease.pyrpc.PyRpcMessage;
import cn.nukkit.network.protocol.netease.pyrpc.PyRpcSubPacket;

/**
 * NetEase PyRpc message received from a client.
 */
@OnlyNetEase
public class PlayerNetEasePyRpcReceivedEvent extends PlayerNetEasePyRpcEvent<PyRpcSubPacket> implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    public PlayerNetEasePyRpcReceivedEvent(Player player, long msgId, PyRpcMessage message,
                                           PyRpcSubPacket subPacket) {
        super(player, msgId, message, subPacket);
    }

    public static HandlerList getHandlers() {
        return handlers;
    }
}
