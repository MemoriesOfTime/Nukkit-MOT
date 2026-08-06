package cn.nukkit.network.protocol.v113;

import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.ProtocolInfo;

public class AddItemPacketV113 extends DataPacket_v113 {
    public static final byte NETWORK_ID = ProtocolInfoV113.ADD_ITEM_PACKET;

    @Override
    public byte pid() {
        if(this.protocol < ProtocolInfo.v_1_0_0){
            return ProtocolInfo.oldProtocolInfo.get(this.protocol).get(this.getClass());
        }
        return NETWORK_ID;
    }

    public Item item;

    @Override
    public void decode() {

    }

    @Override
    public void encode() {
        this.reset();
        this.putSlot(protocol, item);
    }
}
