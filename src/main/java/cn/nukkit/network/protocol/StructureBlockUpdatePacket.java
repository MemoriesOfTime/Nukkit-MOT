package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.v113.ProtocolInfoV113;
import lombok.ToString;

@ToString
public class StructureBlockUpdatePacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.STRUCTURE_BLOCK_UPDATE_PACKET;

    @Override
    public byte pid() {
        if(this.protocol < ProtocolInfo.v1_2_0){
            return ProtocolInfoV113.STRUCTURE_BLOCK_UPDATE_PACKET;
        }
        return NETWORK_ID;
    }

    @Override
    public void decode() {
    }

    @Override
    public void encode() {
    }
}
