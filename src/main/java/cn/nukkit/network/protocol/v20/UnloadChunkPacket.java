package cn.nukkit.network.protocol.v20;

import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.v113.ProtocolInfoV113;

public class UnloadChunkPacket  extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_10_0).get(UnloadChunkPacket.class);

    public int chunkX;
    public int chunkZ;


    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {

    }

    @Override
    public void encode() {
        this.tryReset();
        this.putInt(this.chunkX);
        this.putInt(this.chunkZ);
    }
}
