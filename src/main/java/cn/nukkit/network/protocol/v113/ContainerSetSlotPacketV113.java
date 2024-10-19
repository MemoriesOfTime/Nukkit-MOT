package cn.nukkit.network.protocol.v113;

import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.DataPacket;

/**
 * author: MagicDroidX
 * Nukkit Project
 */
public class ContainerSetSlotPacketV113 extends DataPacket {
    public static final byte NETWORK_ID = ProtocolInfoV113.CONTAINER_SET_SLOT_PACKET;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    public int windowid;
    public int slot;
    public int hotbarSlot;
    public Item item;
    public int selectedSlot;

    @Override
    public void decode() {
        this.windowid = this.getByte();
        this.slot = this.getVarInt();
        this.hotbarSlot = this.getVarInt();
        this.item = this.getSlot(protocol);
        this.selectedSlot = this.getByte();
    }

    @Override
    public void encode() {
        this.reset();
        this.putByte((byte) this.windowid);
        this.putVarInt(this.slot);
        this.putVarInt(this.hotbarSlot);
        this.putSlot(protocol, this.item);
        this.putByte((byte) this.selectedSlot);
    }
}
