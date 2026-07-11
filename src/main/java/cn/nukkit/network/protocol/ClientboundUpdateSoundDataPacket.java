package cn.nukkit.network.protocol;

import lombok.ToString;

/**
 * Sent to update sound data.
 *
 * @since v1001
 */
@ToString
public class ClientboundUpdateSoundDataPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.CLIENTBOUND_UPDATE_SOUND_DATA_PACKET;

    public long serverSoundHandle;
    public String type;

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
        this.serverSoundHandle = this.getLLong();
        this.type = this.getString();
    }

    @Override
    public void encode() {
        this.reset();
        this.putLLong(this.serverSoundHandle);
        this.putString(this.type != null ? this.type : "");
    }
}
