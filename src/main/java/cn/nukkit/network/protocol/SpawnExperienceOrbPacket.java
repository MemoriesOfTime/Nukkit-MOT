package cn.nukkit.network.protocol;

import lombok.ToString;

@ToString
public class SpawnExperienceOrbPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.SPAWN_EXPERIENCE_ORB_PACKET;

    public float x;
    public float y;
    public float z;
    public int amount;

    @Override
    public void decode() {
        this.decodeUnsupported();
    }

    @Override
    public void encode() {
        this.reset();
        this.putVector3f(this.x, this.y, this.z);
        this.putVarInt(this.amount);
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }
}
