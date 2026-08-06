package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.v113.ProtocolInfoV113;
import lombok.ToString;

@ToString
public class ShowCreditsPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.SHOW_CREDITS_PACKET;

    public static final int STATUS_START_CREDITS = 0;
    public static final int STATUS_END_CREDITS = 1;

    public long eid;
    public int status;

    @Override
    public byte pid() {
        if(this.protocol < ProtocolInfo.v1_2_0){
            return ProtocolInfoV113.SHOW_CREDITS_PACKET;
        }
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        this.eid = this.getEntityRuntimeId();
        this.status = this.getVarInt();
    }

    @Override
    public void encode() {
        if (this.protocol >= ProtocolInfo.v1_2_0){
            this.reset();
            this.putEntityRuntimeId(this.eid);
        }else{
            this.putEntityUniqueId(this.eid);
        }
        this.putVarInt(this.status);
    }
}
