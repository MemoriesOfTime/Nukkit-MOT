package cn.nukkit.network.protocol;

import lombok.ToString;

/**
 * @author glorydark
 */
@ToString
public class CurrentStructureFeaturePacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.CURRENT_STRUCTURE_FEATURE_PACKET;

    public String currentStructureFeature;

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
        this.currentStructureFeature = this.getString();
    }

    @Override
    public void encode() {
        this.putString(this.currentStructureFeature);
    }
}
