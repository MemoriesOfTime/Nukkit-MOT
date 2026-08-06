package cn.nukkit.network.protocol.v20;

import cn.nukkit.math.BlockVector3;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;

public class MoveEntityPosRotPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(MoveEntityPosRotPacket.class);

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    public long eid;
    public float x;
    public float y;
    public float z;
    public float yaw;
    public float pitch;

    @Override
    public void decode() {
        this.get(7);
        this.eid = this.getInt();
        this.x = this.getInt();
        this.y = this.getInt();
        this.z = this.getByte();
        this.yaw = this.getFloat();
        this.pitch = this.getFloat();
    }

    @Override
    public void encode() {
        this.tryReset();
        this.putInt((int) this.eid);
        this.putFloat(this.x);
        this.putFloat(this.y);
        this.putFloat(this.z);
        this.putFloat(this.yaw);
        this.putFloat(this.pitch);
    }
}
