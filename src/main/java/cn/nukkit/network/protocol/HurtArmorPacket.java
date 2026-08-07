package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.v113.ProtocolInfo_v113;
import lombok.ToString;

/**
 * @author Nukkit Project Team
 */
@ToString
public class HurtArmorPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.HURT_ARMOR_PACKET;

    public int cause;
    public int damage;
    public long armorSlots;

    @Override
    public void decode() {
        this.decodeUnsupported();
    }

    @Override
    public void encode() {
        if(this.protocol <= ProtocolInfo.v_0_15_10){
            this.tryReset();
            this.putByte((byte) (damage & 0xff));
            return;
        }

        this.reset();
        if (protocol >= ProtocolInfo.v1_16_0) {
            this.putVarInt(this.cause);
        }
        this.putVarInt(this.damage);
        if (protocol >= ProtocolInfo.v1_17_30) {
            this.putUnsignedVarLong(this.armorSlots);
        }
    }

    @Override
    public byte pid() {
        if(this.protocol >= ProtocolInfo.v1_2_0){
            return NETWORK_ID;
        }else if(this.protocol < ProtocolInfo.v_1_0_0){
            return ProtocolInfo.oldProtocolInfo.get(this.protocol).get(this.getClass());
        }else if(this.protocol < ProtocolInfo.v1_2_0){
            return ProtocolInfo_v113.HURT_ARMOR_PACKET;
        }
        return NETWORK_ID;
    }
}
