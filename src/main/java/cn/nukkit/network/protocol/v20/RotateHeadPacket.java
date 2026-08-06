package cn.nukkit.network.protocol.v20;

import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;

import java.util.UUID;

public class RotateHeadPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(RotateHeadPacket.class);

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    public long eid;
    public double headYaw;

    @Override
    public void decode() {
        this.get(7);
        this.eid = this.getInt();
        this.headYaw = this.getByte();
    }

    @Override
    public void encode() {
        this.tryReset();
        this.putInt(1);
        this.putInt((int) this.eid);
        this.putByte((byte) this.headYaw);
    }
}
