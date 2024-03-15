package cn.nukkit.network.protocol.v113;

import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.DataPacket;

/**
 * @author Nukkit Project Team
 */
public class DropItemPacketV113 extends DataPacket {

    public static final byte NETWORK_ID = 0x2e;

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
