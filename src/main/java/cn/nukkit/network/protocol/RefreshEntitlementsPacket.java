package cn.nukkit.network.protocol;

/**
 * @since v618
 */
public class RefreshEntitlementsPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.REFRESH_ENTITLEMENTS_PACKET;

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
        this.decodeUnsupported();
    }

    @Override
    public void encode() {
        this.encodeUnsupported();
    }
}
