package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.types.DisconnectFailReason;
import lombok.ToString;

@ToString
public class DisconnectPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.DISCONNECT_PACKET;

    public DisconnectFailReason reason = DisconnectFailReason.DISCONNECTED;
    public boolean hideDisconnectionScreen = false;
    public String message;
    /**
     * @since v712
     */
    public String filteredMessage = "";

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        if (protocol >= ProtocolInfo.v1_20_40) {
            this.reason = DisconnectFailReason.values()[this.getVarInt()];
        }
        if (protocol >= ProtocolInfo.v1_26_20_26) {
            this.hideDisconnectionScreen = this.getUnsignedVarInt() != 0;
        } else {
            this.hideDisconnectionScreen = this.getBoolean();
        }
        if (!this.hideDisconnectionScreen) {
            this.message = this.getString();
            if (protocol >= ProtocolInfo.v1_21_20) {
                this.filteredMessage = this.getString();
            }
        }
    }

    @Override
    public void encode() {
        this.reset();
        if (protocol >= ProtocolInfo.v1_20_40) {
            this.putVarInt(this.reason.ordinal());
        }
        if (protocol >= ProtocolInfo.v1_26_20_26) {
            this.putUnsignedVarInt(this.hideDisconnectionScreen ? 1 : 0);
        } else {
            this.putBoolean(this.hideDisconnectionScreen);
        }
        if (!this.hideDisconnectionScreen) {
            this.putString(this.message);
            if (protocol >= ProtocolInfo.v1_21_20) {
                this.putString(this.filteredMessage);
            }
        }
    }
}
