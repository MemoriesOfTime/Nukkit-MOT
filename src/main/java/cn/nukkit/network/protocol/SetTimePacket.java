package cn.nukkit.network.protocol;

import lombok.ToString;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
@ToString
public class SetTimePacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.SET_TIME_PACKET;

    public int time;

    public boolean started = true;// 0.14.3 加入的值

    @Override
    public byte pid() {
        if(this.protocol >= ProtocolInfo.v1_2_0){
            return NETWORK_ID;
        }else if(this.protocol < ProtocolInfo.v_1_0_0){
            return ProtocolInfo.oldProtocolInfo.get(this.protocol).get(this.getClass());
        }
        return NETWORK_ID;
    }

    @Override
    public void decode() {
    }

    @Override
    public void encode() {
        if(this.protocol < ProtocolInfo.v_1_0_0){
            this.tryReset();
            if(this.protocol >= ProtocolInfo.v_0_16_0){
                this.putUnsignedVarInt(this.time);
                this.putBoolean(this.started);
                return;
            }
            this.putInt(this.time);
            if(this.protocol <= ProtocolInfo.v_0_11_0){
                this.putByte((byte) (this.started ? 0x80 : 0x00));
                return;
            }
            this.putByte((byte) (this.started ? 1 : 0));
            return;
        }

        this.reset();
        this.putVarInt(this.time);
    }
}
