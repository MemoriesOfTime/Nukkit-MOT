package cn.nukkit.network.protocol;

import lombok.ToString;

/**
 * Sent by the client to provide additional client metadata about party changes.
 *
 * @since v944
 */
@ToString
public class PartyChangedPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.PARTY_CHANGED_PACKET;

    public String partyId;
    public boolean isPartyLeader;

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
        this.partyId = this.getString();
        if (protocol >= 975) {
            this.isPartyLeader = this.getBoolean();
        }
    }

    @Override
    public void encode() {
        this.reset();
        this.putString(this.partyId);
        if (protocol >= 975) {
            this.putBoolean(this.isPartyLeader);
        }
    }
}
