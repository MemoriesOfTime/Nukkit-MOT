package cn.nukkit.network.protocol;

import lombok.ToString;

@ToString
public class InteractPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.INTERACT_PACKET;

    public static final int ACTION_VEHICLE_EXIT = 3;
    public static final int ACTION_MOUSEOVER = 4;
    public static final int ACTION_OPEN_NPC = 5;
    public static final int ACTION_OPEN_INVENTORY = 6;

    public int action;
    public long target;

    public float x;
    public float y;
    public float z;

    @Override
    public void decode() {
        this.action = this.getByte();
        this.target = this.getEntityRuntimeId();
        boolean b;
        if (this.protocol >= ProtocolInfo.v1_21_130_28) {
            b = this.getBoolean();
        } else {
            b = this.action == ACTION_MOUSEOVER || this.action == ACTION_VEHICLE_EXIT;
        }
        if (b) {
            this.x = this.getFloat();
            this.y = this.getFloat();
            this.z = this.getFloat();
        }
    }

    @Override
    public void encode() {
        if (this.protocol >= ProtocolInfo.v1_21_130_28) {
            this.encodeUnsupported();
        } else {
            this.reset();
            this.putByte((byte) this.action);
            this.putEntityRuntimeId(this.target);
        }
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }
}
