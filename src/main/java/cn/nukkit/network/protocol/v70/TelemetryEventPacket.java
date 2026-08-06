package cn.nukkit.network.protocol.v70;

import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;

public class TelemetryEventPacket extends DataPacket {
    @Override
    public byte pid() {
        return ProtocolInfo.oldProtocolInfo.get(this.protocol).get(this.getClass());
    }

    public long entityId;
    public int unknown1; //always 0x00000003?
    public int fromDimension; //?
    public int toDimension; //?

    @Override
    public void decode() {

    }

    @Override
    public void encode() {
        this.tryReset();
        this.putLong(entityId);
        this.putInt(unknown1);
        this.putInt(fromDimension);
        this.putInt(toDimension);
    }
}
