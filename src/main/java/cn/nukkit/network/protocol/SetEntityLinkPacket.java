package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.v113.ProtocolInfoV113;
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
        if(this.protocol < ProtocolInfo.v_1_0_0){
            this.tryReset();
            if(this.protocol >= ProtocolInfo.v_0_16_0){
                this.putEntityUniqueId(riderUniqueId);// rider
                this.putEntityUniqueId(vehicleUniqueId);// riding
                this.putByte(this.type);
                return;
            }
            if(this.protocol > ProtocolInfo.v_0_10_0){
                this.putLong(riderUniqueId);// rider
                this.putLong(vehicleUniqueId);// riding
                this.putByte(type);
            }else{
                this.putInt((int) riderUniqueId);
                this.putInt((int) vehicleUniqueId);
                this.putInt(type);
            }
            return;
        }
        this.reset();
        if (protocol >= ProtocolInfo.v1_2_0) {
            this.putEntityUniqueId(this.vehicleUniqueId);
            this.putEntityUniqueId(this.riderUniqueId);
        }else{
            this.putEntityUniqueId(this.riderUniqueId);
            this.putEntityUniqueId(this.vehicleUniqueId);
        }
        this.putByte(this.type);
        if (protocol < ProtocolInfo.v1_2_0) {
            return;
        }
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
        if(this.protocol >= ProtocolInfo.v1_2_0){
            return NETWORK_ID;
        }else if(this.protocol < ProtocolInfo.v_1_0_0){
            return ProtocolInfo.oldProtocolInfo.get(this.protocol).get(this.getClass());
        }else if(this.protocol < ProtocolInfo.v1_2_0){
            return ProtocolInfoV113.SET_ENTITY_LINK_PACKET;
        }
        return NETWORK_ID;
    }
}
