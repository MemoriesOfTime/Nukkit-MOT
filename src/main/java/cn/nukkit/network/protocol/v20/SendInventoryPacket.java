package cn.nukkit.network.protocol.v20;

import cn.nukkit.entity.data.EntityMetadata;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.Binary;

public class SendInventoryPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_16_0).get(SendInventoryPacket.class);

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    public long entityUniqueId;
    public int windowId;
    public int slots;// 0.14.3
    public int armor;

    @Override
    public void decode() {

    }

    @Override
    public void encode() {
        this.tryReset();
        this.putInt((int) this.entityUniqueId);
        this.putInt(this.windowId);
        this.putShort(this.slots);
    }
}
