package cn.nukkit.network.protocol.v20;

import cn.nukkit.entity.data.EntityMetadata;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.Binary;

public class AddMobPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_10_0).get(AddMobPacket.class);

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    public long entityUniqueId;
    public int type;
    public float x;
    public float y;
    public float z;
    public float pitch;
    public float yaw;
    public EntityMetadata metadata = new EntityMetadata();

    @Override
    public void decode() {

    }

    @Override
    public void encode() {
        this.tryReset();
        this.putInt((int) this.entityUniqueId);
        this.putInt(this.type);
        this.putInt((int) this.x);
        this.putInt((int) this.y);
        this.putInt((int) this.z);
        this.putByte((byte) this.yaw);
        this.putByte((byte) this.pitch);
        this.put(Binary.writeMetadata_old(this.metadata));
    }
}
