package cn.nukkit.network.protocol;

import lombok.ToString;

/**
 * Sent by the client to the server with a party destination cookie response.
 *
 * @since v1001
 */
@ToString
public class PartyDestinationCookieResponsePacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.PARTY_DESTINATION_COOKIE_RESPONSE_PACKET;

    public String cookie;
    public boolean accepted;

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
        this.cookie = this.getString();
        this.accepted = this.getBoolean();
    }

    @Override
    public void encode() {
        this.encodeUnsupported();
    }
}
