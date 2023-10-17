package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.types.DisconnectFailReason;
import lombok.ToString;

@ToString
public class DisconnectPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.DISCONNECT_PACKET;

    private DisconnectFailReason reason = DisconnectFailReason.UNKNOWN;
    public boolean hideDisconnectionScreen = false;
    public String message;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        if (protocol >= ProtocolInfo.v1_20_40_21) {
            this.reason = DisconnectFailReason.values()[this.getVarInt()];
        }
        this.hideDisconnectionScreen = this.getBoolean();
        this.message = this.getString();
    }

    @Override
    public void encode() {
        this.reset();
        if (protocol >= ProtocolInfo.v1_20_40_21) {
            this.putVarInt(this.reason.ordinal());
        }
        this.putBoolean(this.hideDisconnectionScreen);
        if (!this.hideDisconnectionScreen) {
            this.putString(this.message);
        }
    }
}
