package cn.nukkit.network.protocol.v70;

import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;

public class DropItemPacket  extends DataPacket {
    @Override
    public byte pid() {
        return ProtocolInfo.oldProtocolInfo.get(this.protocol).get(this.getClass());
    }

    public long eid;
    public int type;
    public Item item;

    @Override
    public void decode() {
        if(this.protocol <= ProtocolInfo.v_0_11_0 && this.protocol > ProtocolInfo.v_0_10_0){
            this.eid = getLong();
        } else if (this.protocol <= ProtocolInfo.v_0_10_0) {
            this.eid = getInt();
        }
        this.type = getByte();
        this.item = getSlot_old(this.protocol);
    }

    @Override
    public void encode() {
        this.tryReset();
    }
}
