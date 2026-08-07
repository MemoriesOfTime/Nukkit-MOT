package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.v113.ProtocolInfoV113;
import lombok.ToString;

@ToString
public class EntityFallPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.ENTITY_FALL_PACKET;

    public long eid;
    public float fallDistance;
    public boolean isInVoid;

    @Override
    public void decode() {
        if(this.protocol >= ProtocolInfo.v1_2_0){
            this.eid = this.getEntityRuntimeId();
        }else{
            this.eid = this.getEntityUniqueId();
        }
        this.fallDistance = this.getLFloat();
        this.isInVoid = this.getBoolean();
    }

    @Override
    public void encode() {
        this.encodeUnsupported();
    }

    @Override
    public byte pid() {
        if(this.protocol < ProtocolInfo.v1_2_0){
            return ProtocolInfoV113.ENTITY_FALL_PACKET;
        }
        return NETWORK_ID;
    }
}
