package cn.nukkit.network.protocol;

import lombok.ToString;

@ToString
public class TickingAreasLoadStatusPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.TICKING_AREAS_LOAD_STATUS_PACKET;
    boolean waitingForPreload;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
    }

    @Override
    public void encode() {
        this.putBoolean(this.waitingForPreload);
    }
}
