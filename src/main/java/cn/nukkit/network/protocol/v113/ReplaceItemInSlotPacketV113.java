package cn.nukkit.network.protocol.v113;

import cn.nukkit.item.Item;

/**
 * Created by Pub4Game on 29.04.2016.
 */
public class ReplaceItemInSlotPacketV113 extends DataPacket_v113 {

    public static final byte NETWORK_ID = ProtocolInfoV113.REPLACE_ITEM_IN_SLOT_PACKET;

    public Item item;

    @Override
    public void decode() {

    }

    @Override
    public void encode() {
        this.reset();
        this.putSlot(gameVersion, this.item);
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

}