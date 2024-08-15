package cn.nukkit.network.protocol;

import lombok.ToString;

@ToString
public class SetEntityLinkPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.SET_ENTITY_LINK_PACKET;

    public static final byte TYPE_REMOVE = 0;
    public static final byte TYPE_RIDE = 1;
    public static final byte TYPE_PASSENGER = 2;

    public long vehicleUniqueId;
    public long riderUniqueId;
    public byte type;
    public byte immediate;
    public boolean riderInitiated = false;
    /**
     * @since v712
     */
    public float vehicleAngularVelocity;

    @Override
    public void decode() {
    }

    @Override
    public void encode() {
        this.reset();
        this.putEntityUniqueId(this.vehicleUniqueId);
        this.putEntityUniqueId(this.riderUniqueId);
        this.putByte(this.type);
        this.putByte(this.immediate);
        if (protocol >= 407) {
            this.putBoolean(this.riderInitiated);
            if (this.protocol >= ProtocolInfo.v1_21_20) {
                this.putLFloat(this.vehicleAngularVelocity);
            }
        }
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }
}
