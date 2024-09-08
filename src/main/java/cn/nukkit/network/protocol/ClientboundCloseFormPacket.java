package cn.nukkit.network.protocol;

import lombok.ToString;

/**
 * @since 686
 */
@ToString
public class ClientboundCloseFormPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.CLIENTBOUND_CLOSE_FORM_PACKET;

    @Override
    public int packetId() {
        return NETWORK_ID;
    }

    @Override
    public byte pid() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public void decode() {

    }

    @Override
    public void encode() {
        this.reset();
        // No payload
    }
}
