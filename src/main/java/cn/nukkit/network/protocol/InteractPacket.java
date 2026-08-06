package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.v113.ProtocolInfoV113;
import lombok.ToString;

@ToString
public class InteractPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.INTERACT_PACKET;

    public static final int ACTION_VEHICLE_EXIT = 3;
    public static final int ACTION_MOUSEOVER = 4;
    public static final int ACTION_OPEN_NPC = 5;
    public static final int ACTION_OPEN_INVENTORY = 6;

    public int eid;

    public int action;
    public long target;

    @Override
    public void decode() {
        this.action = (byte) getByte();
        if(this.protocol <= ProtocolInfo.v_0_15_10){
            if(this.protocol > ProtocolInfo.v_0_10_0){
                this.target = this.getLong();
                return;
            }else{
                this.eid = this.getInt();
                this.target = this.getInt();
                return;
            }
        }else if(this.protocol <= ProtocolInfo.v1_2_0){
            this.target = this.getEntityUniqueId();
            return;
        }
        this.target = this.getEntityRuntimeId();
    }

    @Override
    public void encode() {
        if(this.protocol <= ProtocolInfo.v_0_15_10){
            this.tryReset();
            this.putByte((byte) (action & 0xff));
            if(this.protocol > ProtocolInfo.v_0_10_0){
                this.putLong(target);
                return;
            }else{
                this.putInt(this.eid);
                this.putInt((int) this.target);
                return;
            }
        }
        this.reset();
        this.putByte((byte) this.action);
        if(this.protocol >= ProtocolInfo.v1_2_0){
            this.putEntityRuntimeId(this.target);
        }else{
            this.putEntityUniqueId(this.target);
        }
    }

    @Override
    public byte pid() {
        if(this.protocol >= ProtocolInfo.v1_2_0){
            return NETWORK_ID;
        }else if(this.protocol < ProtocolInfo.v_1_0_0){
            return ProtocolInfo.oldProtocolInfo.get(this.protocol).get(this.getClass());
        }else if(this.protocol < ProtocolInfo.v1_2_0){
            return ProtocolInfoV113.INTERACT_PACKET;
        }
        return NETWORK_ID;
    }
}
