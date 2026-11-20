package cn.nukkit.network.protocol;

import lombok.ToString;

/**
 * @since v2225
 */
@ToString
public class ServerboundMatchmakingCancelPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.SERVERBOUND_MATCHMAKING_CANCEL_PACKET;

    @Override
    @Deprecated
    public byte pid() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public int packetId() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        this.decodeUnsupported();
    }

    @Override
    public void encode() {
        this.reset();
    }
}
