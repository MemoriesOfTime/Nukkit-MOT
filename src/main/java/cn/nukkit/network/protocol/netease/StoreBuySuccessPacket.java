package cn.nukkit.network.protocol.netease;

import cn.nukkit.api.OnlyNetEase;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import lombok.ToString;

/**
 * Sent by NetEase clients and servers when a store purchase succeeds.
 */
@OnlyNetEase
@ToString
public class StoreBuySuccessPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.STORE_BUY_SUCCESS_PACKET;

    @Override
    public int packetId() {
        return NETWORK_ID;
    }

    @Override
    @Deprecated
    public byte pid() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public void decode() {
    }

    @Override
    public void encode() {
        this.reset();
    }
}
