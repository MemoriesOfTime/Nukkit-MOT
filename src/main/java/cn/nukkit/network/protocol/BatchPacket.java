package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.v113.ProtocolInfoV113;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class BatchPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.BATCH_PACKET;

    public byte[] payload;

    @Override
    public byte pid() {
        if(this.protocol >= ProtocolInfo.v1_2_0){
            return NETWORK_ID;
        }else if(this.protocol < ProtocolInfo.v_1_0_0){
            return ProtocolInfo.oldProtocolInfo.get(this.protocol).get(this.getClass());
        }else if(this.protocol < ProtocolInfo.v1_2_0){
            return ProtocolInfoV113.BATCH_PACKET;
        }
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        if(this.protocol <= ProtocolInfo.v_0_15_10){
            this.payload = this.get(this.getInt());//0.12-0.15
            return;
        }else if(this.protocol < ProtocolInfo.v_1_0_0){// 0.16
            this.payload = this.getByteArray();//0.16
            return;
        }
        this.payload = this.get();
    }

    @Override
    public void encode() {
        if(this.protocol <= ProtocolInfo.v_0_15_10){// 0.12-0.15
            this.tryReset();
            this.putInt(this.payload.length);
            this.put(this.payload);
            return;
        }else if(this.protocol < ProtocolInfo.v_1_0_0){// 0.16
            this.putByteArray(this.payload);
            return;
        }
    }

    public void trim() {
        setBuffer(null);
    }

    @Override
    public BatchPacket clone() {
        BatchPacket packet = (BatchPacket) super.clone();
        if (this.payload != null) {
            packet.payload = this.payload.clone();
        }
        return packet;
    }
}
