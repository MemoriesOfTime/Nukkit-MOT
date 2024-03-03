package cn.nukkit.network.protocol.v113;

import cn.nukkit.math.BlockVector3;
import cn.nukkit.network.protocol.DataPacket;

/**
 * @author Nukkit Project Team
 */
public class RemoveBlockPacketV113 extends DataPacket {

    public static final byte NETWORK_ID = /*ProtocolInfo.REMOVE_BLOCK_PACKET*/ 0x15;

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
