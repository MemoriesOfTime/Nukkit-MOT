package cn.nukkit.network.protocol;

import lombok.ToString;

@ToString
public class InteractPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.INTERACT_PACKET;

    /**
     * Only used by protocol v113 (1.1). Since v137 (1.2), entity interaction is handled by InventoryTransactionPacket.
     */
    public static final int ACTION_RIGHT_CLICK = 1;
    /**
     * Only used by protocol v113 (1.1). Since v137 (1.2), entity attack is handled by InventoryTransactionPacket.
     */
    public static final int ACTION_LEFT_CLICK = 2;
    public static final int ACTION_VEHICLE_EXIT = 3;
    public static final int ACTION_MOUSEOVER = 4;
    public static final int ACTION_OPEN_NPC = 5;
    public static final int ACTION_OPEN_INVENTORY = 6;

    public int eid;

    public int action;
    public long target;
    public float x;
    public float y;
    public float z;

    @Override
    public void decode() {
        this.action = this.getByte();
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
        if (this.hasPositionData()) {
            this.x = this.getLFloat();
            this.y = this.getLFloat();
            this.z = this.getLFloat();
        }
    }

    private boolean hasPositionData() {
        if (protocol >= ProtocolInfo.v1_21_130_28) {
            return this.getBoolean();
        }
        if (protocol < ProtocolInfo.v1_2_0) {
            return false; // v113 (1.1) InteractPacket does not include position data
        }
        return this.action == ACTION_MOUSEOVER
                || (protocol >= ProtocolInfo.v1_13_0 && this.action == ACTION_VEHICLE_EXIT);
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
        this.encodeUnsupported();
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
