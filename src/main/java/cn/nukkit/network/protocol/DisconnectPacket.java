package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.types.DisconnectFailReason;
import lombok.ToString;

@ToString
public class DisconnectPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.DISCONNECT_PACKET;

    public DisconnectFailReason reason = DisconnectFailReason.UNKNOWN;
    public boolean hideDisconnectionScreen = false;
    public String message;
    /**
     * @since v712
     */
    public String filteredMessage = "";

    @Override
    public byte pid() {
        if(this.protocol < ProtocolInfo.v_1_0_0){
            return ProtocolInfo.oldProtocolInfo.get(this.protocol).get(this.getClass());
        }
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        if(this.protocol <= ProtocolInfo.v_0_15_10){
            this.message = this.getString_old();
            return;
        }

        if (protocol >= ProtocolInfo.v1_20_40) {
            this.reason = DisconnectFailReason.values()[this.getVarInt()];
        }
        this.hideDisconnectionScreen = this.getBoolean();
        if (!this.hideDisconnectionScreen) {
            this.message = this.getString();
            if (protocol >= ProtocolInfo.v1_21_20) {
                this.filteredMessage = this.getString();
            }
        }
    }

    @Override
    public void encode() {
        if(this.protocol < ProtocolInfo.v_1_0_0){
            this.tryReset();
            if(this.protocol >= ProtocolInfo.v_0_16_0){
                this.putBoolean(this.hideDisconnectionScreen);
                this.putString(this.message);
                return;
            }
            this.putString_old(this.message);
            return;
        }

        this.reset();
        if (protocol >= ProtocolInfo.v1_20_40) {
            this.putVarInt(this.reason.ordinal());
        }
        this.putBoolean(this.hideDisconnectionScreen);
        if (!this.hideDisconnectionScreen) {
            this.putString(this.message);
            if (protocol >= ProtocolInfo.v1_21_20) {
                this.putString(this.filteredMessage);
            }
        }
    }
}
