package cn.nukkit.network.protocol;

import lombok.ToString;

/**
 * Initializes and syncs world clocks from server to client.
 *
 * @since v944
 */
@ToString
public class SyncWorldClocksPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.SYNC_WORLD_CLOCKS_PACKET;

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
        // TODO: implement world clock serialization when needed
    }
}
