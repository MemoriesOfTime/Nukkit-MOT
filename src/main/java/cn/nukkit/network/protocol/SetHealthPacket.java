package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.v113.ProtocolInfoV113;
import lombok.ToString;

@ToString
public class SetHealthPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.SET_HEALTH_PACKET;

    public int health;

    @Override
    public byte pid() {
        if(this.protocol >= ProtocolInfo.v1_2_0){
            return NETWORK_ID;
        }else if(this.protocol < ProtocolInfo.v_1_0_0){
            return ProtocolInfo.oldProtocolInfo.get(this.protocol).get(this.getClass());
        }else if(this.protocol < ProtocolInfo.v1_2_0){
            return ProtocolInfoV113.SET_HEALTH_PACKET;
        }
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        if(this.protocol <= ProtocolInfo.v_0_11_0 && this.protocol > ProtocolInfo.v_0_10_0){
            this.health = this.getInt();
        } else if (this.protocol <= ProtocolInfo.v_0_10_0) {
            this.health = this.getByte();
        }
    }

    @Override
    public void encode() {
        if(this.protocol <= ProtocolInfo.v_0_11_0){
            this.tryReset();
            if(this.protocol > ProtocolInfo.v_0_10_0){
                this.putInt(this.health);
            }else{
                this.putByte((byte) this.health);
            }
            return;
        } else if(this.protocol < ProtocolInfo.v_1_0_0 && this.protocol >= ProtocolInfo.v_0_16_0){
            this.tryReset();
            this.putUnsignedVarInt(this.health);
        }
        this.reset();
        this.putUnsignedVarInt(this.health);
    }
}
