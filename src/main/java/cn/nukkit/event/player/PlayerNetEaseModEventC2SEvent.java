package cn.nukkit.event.player;

import cn.nukkit.Player;
import cn.nukkit.api.OnlyNetEase;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;
import cn.nukkit.network.protocol.netease.pyrpc.PyRpcMessage;
import cn.nukkit.network.protocol.netease.pyrpc.subpacket.ModEventPyRpcSubPacket;

import java.util.Map;

/**
 * NetEase client-to-server mod event carried by PyRpcPacket.
 */
@OnlyNetEase
public class PlayerNetEaseModEventC2SEvent extends PlayerNetEasePyRpcEvent<ModEventPyRpcSubPacket> implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    public PlayerNetEaseModEventC2SEvent(Player player, long msgId, PyRpcMessage message,
                                         ModEventPyRpcSubPacket subPacket) {
        super(player, msgId, message, subPacket);
    }

    public String getModName() {
        return getSubPacket().getModName();
    }

    public String getSystemName() {
        return getSubPacket().getSystemName();
    }

    public String getCustomEventName() {
        return getSubPacket().getEventName();
    }

    public Map<String, Object> getEventData() {
        return getSubPacket().getEventData();
    }

    public Map<String, Object> getArgs() {
        return getEventData();
    }

    public static HandlerList getHandlers() {
        return handlers;
    }
}
