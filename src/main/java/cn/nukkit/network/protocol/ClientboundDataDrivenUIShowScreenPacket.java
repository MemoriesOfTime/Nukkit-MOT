package cn.nukkit.network.protocol;

import lombok.ToString;

/**
 * Allows the server to tell the client to show a Data Driven UI screen.
 * @since v924
 */
@ToString
public class ClientboundDataDrivenUIShowScreenPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.CLIENTBOUND_DATA_DRIVEN_UI_SHOW_SCREEN_PACKET;

    /**
     * The ID of the screen to show.
     */
    public String screenId;

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
        this.screenId = this.getString();
    }

    @Override
    public void encode() {
        this.reset();
        this.putString(this.screenId);
    }
}
