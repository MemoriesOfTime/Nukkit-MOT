package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.v113.ProtocolInfoV113;
import lombok.ToString;

@ToString
public class SimpleEventPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.SIMPLE_EVENT_PACKET;

    public static final int TYPE_ENABLE_COMMANDS = 1;
    public static final int TYPE_DISABLE_COMMANDS = 2;

    public short eventType;

    @Override
    public byte pid() {
        if(this.protocol < ProtocolInfo.v1_2_0){
            return ProtocolInfoV113.SIMPLE_EVENT_PACKET;
        }
        return NETWORK_ID;
    }

    @Override
    public void decode() {
    }

    @Override
    public void encode() {
        this.reset();
        this.putShort(this.eventType);
    }
}
