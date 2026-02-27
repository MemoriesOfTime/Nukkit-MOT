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

    public int action;
    public long target;
    public float x;
    public float y;
    public float z;

    @Override
    public void decode() {
        this.action = this.getByte();
        this.target = this.getEntityRuntimeId();
        if (this.hasPositionData()) {
            this.x = this.getFloat();
            this.y = this.getFloat();
            this.z = this.getFloat();
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
        this.encodeUnsupported();
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }
}
