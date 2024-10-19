package cn.nukkit.network.protocol.v113;

import cn.nukkit.math.BlockVector3;
import cn.nukkit.network.protocol.DataPacket;

public class AddHangingEntityPacketV113 extends DataPacket {
    public static final byte NETWORK_ID = ProtocolInfoV113.ADD_HANGING_ENTITY_PACKET;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    public long entityUniqueId;
    public long entityRuntimeId;
    public int x;
    public int y;
    public int z;
    public int unknown;

    @Override
    public void decode() {
        this.entityUniqueId = this.getVarLong();
        this.entityRuntimeId = this.getVarLong();
        BlockVector3 v3 = this.getBlockVector3();
        this.x = v3.x;
        this.y = v3.y;
        this.z = v3.z;
        this.unknown = this.getVarInt();
    }

    @Override
    public void encode() {
        this.reset();
        this.putVarLong(this.entityUniqueId);
        this.putVarLong(this.entityRuntimeId);
        this.putBlockVector3(this.x, this.y, this.z);
        this.putVarInt(this.unknown);
    }
}
