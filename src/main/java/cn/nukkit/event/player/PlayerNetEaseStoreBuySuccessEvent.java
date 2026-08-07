package cn.nukkit.event.player;

import cn.nukkit.Player;
import cn.nukkit.api.OnlyNetEase;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;
import cn.nukkit.network.protocol.netease.pyrpc.PyRpcMessage;
import cn.nukkit.network.protocol.netease.pyrpc.subpacket.StoreBuySuccessPyRpcSubPacket;

/**
 * NetEase store purchase success notification carried by PyRpcPacket.
 */
@OnlyNetEase
public class PlayerNetEaseStoreBuySuccessEvent extends PlayerNetEasePyRpcEvent<StoreBuySuccessPyRpcSubPacket> implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    public PlayerNetEaseStoreBuySuccessEvent(Player player, long msgId, PyRpcMessage message,
                                            StoreBuySuccessPyRpcSubPacket subPacket) {
        super(player, msgId, message, subPacket);
    }

    public static HandlerList getHandlers() {
        return handlers;
    }
}
