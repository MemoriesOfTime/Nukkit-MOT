package cn.nukkit.network.protocol;

import lombok.ToString;

/**
 * Sent from the client to notify the server that all resource packs are loaded.
 *
 * @since v944
 */
@ToString
public class ResourcePacksReadyForValidationPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.RESOURCE_PACKS_READY_FOR_VALIDATION_PACKET;

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
