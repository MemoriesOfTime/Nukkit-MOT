package cn.nukkit.network.protocol.v113;

import cn.nukkit.item.Item;

/**
 * @author Nukkit Project Team
 */
public class DropItemPacketV113 extends DataPacket_v113 {

    public static final byte NETWORK_ID = ProtocolInfoV113.DROP_ITEM_PACKET;

    public int type;
    public Item item;

    @Override
    public void decode() {
        this.type = this.getByte();
        this.item = this.getSlot(protocol);
    }

    @Override
    public void encode() {

    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

}
