package cn.nukkit.network.protocol.v113;

import cn.nukkit.math.BlockVector3;

/**
 * @author Nukkit Project Team
 */
public class RemoveBlockPacketV113 extends DataPacket_v113 {

    public static final byte NETWORK_ID = ProtocolInfoV113.REMOVE_BLOCK_PACKET;

    public int x;
    public int y;
    public int z;

    @Override
    public void decode() {
        BlockVector3 v = this.getBlockVector3();
        this.x = v.x;
        this.y = v.y;
        this.z = v.z;
    }

    @Override
    public void encode() {
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

}
