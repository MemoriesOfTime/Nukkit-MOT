package cn.nukkit.event.player;

import cn.nukkit.Player;
import cn.nukkit.api.OnlyNetEase;
import cn.nukkit.network.protocol.netease.pyrpc.PyRpcMessage;
import cn.nukkit.network.protocol.netease.pyrpc.PyRpcSubPacket;

/**
 * Base event data for a typed NetEase PyRpc sub-packet received from a client.
 */
@OnlyNetEase
public abstract class PlayerNetEasePyRpcEvent<T extends PyRpcSubPacket> extends PlayerEvent {

    private final long msgId;
    private final PyRpcMessage message;
    private final T subPacket;

    protected PlayerNetEasePyRpcEvent(Player player, long msgId, PyRpcMessage message, T subPacket) {
        this.player = player;
        this.msgId = msgId;
        this.message = message;
        this.subPacket = subPacket;
    }

    public long getMsgId() {
        return msgId;
    }

    public PyRpcMessage getMessage() {
        return message;
    }

    public T getSubPacket() {
        return subPacket;
    }

    public String getMethod() {
        return subPacket != null ? subPacket.getMethod() : null;
    }
}
