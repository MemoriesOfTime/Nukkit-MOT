package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.v113.ProtocolInfo_v113;
import lombok.ToString;

@ToString
public class SetCommandsEnabledPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.SET_COMMANDS_ENABLED_PACKET;

    public boolean enabled;

    @Override
    public byte pid() {
        if(this.protocol >= ProtocolInfo.v1_2_0){
            return NETWORK_ID;
        }else if(this.protocol < ProtocolInfo.v_1_0_0 &&  this.protocol >= ProtocolInfo.v_0_16_0){
            return ProtocolInfo.oldProtocolInfo.get(this.protocol).get(this.getClass());
        }else if(this.protocol < ProtocolInfo.v1_2_0 && this.protocol >= ProtocolInfo.v_0_14_3){
            return ProtocolInfo_v113.SET_COMMANDS_ENABLED_PACKET;
        }
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        this.decodeUnsupported();
    }

    @Override
    public void encode() {
        if(this.protocol < ProtocolInfo.v_1_0_0 && this.protocol >= ProtocolInfo.v_0_16_0){
            this.tryReset();
            this.putBoolean(this.enabled);
            return;
        }
        this.reset();
        this.putBoolean(this.enabled);
    }
}
