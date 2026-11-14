package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.types.PacketCompressionAlgorithm;
import cn.nukkit.network.protocol.types.SctpRecoveryProfile;
import lombok.ToString;

@ToString
public class NetworkSettingsPacket extends DataPacket {

    public int compressionThreshold;
    public PacketCompressionAlgorithm compressionAlgorithm;
    public boolean clientThrottleEnabled;
    public byte clientThrottleThreshold;
    public float clientThrottleScalar;
    /**
     * @since v2259 1.26.70
     */
    public SctpRecoveryProfile sctpRecoveryProfile = SctpRecoveryProfile.DEFAULT;

    @Override
    public byte pid() {
        return ProtocolInfo.NETWORK_SETTINGS_PACKET;
    }

    @Override
    public void encode() {
        this.reset();
        this.putLShort(this.compressionThreshold);
        if (protocol >= ProtocolInfo.v1_19_30) {
            this.putLShort(this.compressionAlgorithm.ordinal());
            this.putBoolean(this.clientThrottleEnabled);
            this.putByte(this.clientThrottleThreshold);
            this.putLFloat(this.clientThrottleScalar);
            if (protocol >= ProtocolInfo.v1_26_70) {
                this.putByte((byte) this.sctpRecoveryProfile.getId());
            }
        }
    }

    @Override
    public void decode() {
        throw new UnsupportedOperationException();
    }
}