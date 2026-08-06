package cn.nukkit.network.protocol;

import lombok.ToString;

@ToString
public class TakeItemEntityPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.TAKE_ITEM_ENTITY_PACKET;

    public long entityId;
    public long target;

    @Override
    public void decode() {
        this.target = this.getEntityRuntimeId();
        this.entityId = this.getEntityRuntimeId();
    }

    @Override
    public void encode() {
        if(this.protocol < ProtocolInfo.v_1_0_0){
            this.tryReset();
            if(this.protocol >= ProtocolInfo.v_0_16_0){
                this.putVarLong(this.target);
                this.putVarLong(this.entityId);
                return;
            } else if (this.protocol <= ProtocolInfo.v_0_10_0) {
                this.putInt((int) this.target);
                this.putInt((int) this.entityId);
                return;
            }
            this.putLong(target);
            this.putLong(entityId);
            return;
        }
        this.reset();
        if(this.protocol >= ProtocolInfo.v1_2_0){
            this.putEntityRuntimeId(this.target);
            this.putEntityRuntimeId(this.entityId);
        }else{
            this.putEntityUniqueId(this.target);
            this.putEntityUniqueId(this.entityId);
        }
    }

    @Override
    public byte pid() {
        if(this.protocol >= ProtocolInfo.v1_2_0){
            return NETWORK_ID;
        }else if(this.protocol < ProtocolInfo.v_1_0_0){
            return ProtocolInfo.oldProtocolInfo.get(this.protocol).get(this.getClass());
        }
        return NETWORK_ID;
    }
}
