package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.v113.ProtocolInfoV113;
import lombok.ToString;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
@ToString
public class BlockEventPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.BLOCK_EVENT_PACKET;

    @Override
    public byte pid() {
        if(this.protocol >= ProtocolInfo.v1_2_0){
            return NETWORK_ID;
        }else if(this.protocol < ProtocolInfo.v_1_0_0){
            return ProtocolInfo.oldProtocolInfo.get(this.protocol).get(this.getClass());
        }else if(this.protocol < ProtocolInfo.v1_2_0){
            return ProtocolInfoV113.BLOCK_EVENT_PACKET;
        }
        return NETWORK_ID;
    }

    public int x;
    public int y;
    public int z;
    public int eventType;
    public int eventData;

    @Deprecated
    public int case1 = -1;
    @Deprecated
    public int case2 = -1;

    @Override
    public void decode() {
        this.decodeUnsupported();
    }

    @Override
    public void encode() {
        //兼容NK插件
        if (this.case1 != -1) {
            this.eventType = this.case1;
        }
        if (this.case2 != -1) {
            this.eventData = this.case2;
        }

        if(this.protocol < ProtocolInfo.v_1_0_0){
            this.tryReset();
            if(this.protocol >= ProtocolInfo.v_0_16_0){
                this.putBlockVector3(this.x, this.y, this.z);
                this.putVarInt(this.case1);
                this.putVarInt(this.case2);
                return;
            }
            this.putInt(this.x);
            this.putInt(this.y);
            this.putInt(this.z);
            this.putInt(this.case1);
            this.putInt(this.case2);
            return;
        }

        this.reset();
        this.putBlockVector3(this.x, this.y, this.z);
        this.putVarInt(this.eventType);
        this.putVarInt(this.eventData);
    }
}
