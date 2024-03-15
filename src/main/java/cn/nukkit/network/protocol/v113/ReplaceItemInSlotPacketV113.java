package cn.nukkit.network.protocol.v113;

import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.DataPacket;

/**
 * Created by Pub4Game on 29.04.2016.
 */
public class ReplaceItemInSlotPacketV113 extends DataPacket {

    public static final byte NETWORK_ID = 0x48;

    public Item item;

    @Override
    public void decode() {

    }

    @Override
    public void encode() {
        this.reset();
        this.putSlot(protocol, this.item);
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

}