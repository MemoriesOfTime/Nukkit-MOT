package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.v113.ProtocolInfoV113;
import lombok.ToString;

/**
 * @deprecated Removed as of v800 (1.21.80). Server authoritative jump is handled by {@link PlayerAuthInputPacket}
 */
@ToString
@SuppressWarnings("dep-ann")
public class RiderJumpPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.RIDER_JUMP_PACKET;

    public int jumpStrength;

    @Override
    public byte pid() {
        if(this.protocol >= ProtocolInfo.v1_2_0){
            return NETWORK_ID;
        }else if(this.protocol < ProtocolInfo.v_1_0_0){
            return ProtocolInfo.oldProtocolInfo.get(this.protocol).get(this.getClass());
        }
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        this.jumpStrength = (int) this.getUnsignedVarInt();
    }

    @Override
    public void encode() {
        if(this.protocol >= ProtocolInfo.v1_2_0){
            this.reset();
        }
        this.putVarInt(this.jumpStrength);
    }
}
