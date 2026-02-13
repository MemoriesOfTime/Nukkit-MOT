package cn.nukkit.network.protocol;

import lombok.ToString;

/**
 * Allows the server to tell the client to close all the Data Driven UI screens.
 * @since v924
 */
@ToString
public class ClientboundDataDrivenUICloseAllScreensPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.CLIENTBOUND_DATA_DRIVEN_UI_CLOSE_ALL_SCREENS_PACKET;

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
        // No data to decode
    }

    @Override
    public void encode() {
        this.reset();
    }
}
