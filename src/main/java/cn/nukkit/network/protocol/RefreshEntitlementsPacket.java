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
        return ProtocolInfo.__INTERNAL__REFRESH_ENTITLEMENTS_PACKET;
    }

    @Override
    public void decode() {
        //TODO
    }

    @Override
    public void encode() {
        //TODO
    }
}
