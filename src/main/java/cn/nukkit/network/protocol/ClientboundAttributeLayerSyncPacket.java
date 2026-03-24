package cn.nukkit.network.protocol;

import lombok.ToString;

/**
 * Syncs attribute layers from server to client.
 *
 * @since v944
 */
@ToString
public class ClientboundAttributeLayerSyncPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.CLIENTBOUND_ATTRIBUTE_LAYER_SYNC_PACKET;

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
        // TODO: implement attribute layer serialization when needed
    }
}
