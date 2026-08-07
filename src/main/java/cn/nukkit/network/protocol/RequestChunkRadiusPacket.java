package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.v113.ProtocolInfoV113;
import lombok.ToString;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
@ToString
public class RequestChunkRadiusPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.REQUEST_CHUNK_RADIUS_PACKET;

    public int radius;
    /**
     * @since v582
     */
    private int maxRadius;

    @Override
    public void decode() {
        if(this.protocol <= ProtocolInfo.v_0_15_10){
            this.radius = this.getInt();
            return;
        }
        this.radius = this.getVarInt();
        if (this.protocol >= ProtocolInfo.v1_19_80) {
            this.maxRadius = this.getByte();
        }
    }

    @Override
    public void encode() {
        this.encodeUnsupported();
    }

    @Override
    public byte pid() {
        if(this.protocol >= ProtocolInfo.v1_2_0){
            return NETWORK_ID;
        }else if(this.protocol < ProtocolInfo.v_1_0_0){
            return ProtocolInfo.oldProtocolInfo.get(this.protocol).get(this.getClass());
        }else if(this.protocol < ProtocolInfo.v1_2_0){
            return ProtocolInfoV113.REQUEST_CHUNK_RADIUS_PACKET;
        }
        return NETWORK_ID;
    }
}
