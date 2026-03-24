package cn.nukkit.network.protocol;

import lombok.ToString;

/**
 * Syncs LocatorBar waypoints from server to client.
 *
 * @since v944
 */
@ToString
public class LocatorBarPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.LOCATOR_BAR_PACKET;

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
        // TODO: implement waypoint serialization when needed
    }
}
