package cn.nukkit.network.protocol.v113;

import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.DataPacket;

public class AddItemPacketV113 extends DataPacket {
    public static final byte NETWORK_ID = 0x4b;
    public Item item;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {

    }

    @Override
    public void encode() {
        this.reset();
        this.putSlot(protocol, item);
    }
}
