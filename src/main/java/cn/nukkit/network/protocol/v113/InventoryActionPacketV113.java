package cn.nukkit.network.protocol.v113;

import cn.nukkit.item.Item;

public class InventoryActionPacketV113 extends DataPacket_v113 {

    public static final byte NETWORK_ID = ProtocolInfoV113.INVENTORY_ACTION_PACKET;

    public int actionId;
    public Item item;
    public int enchantmentId;
    public int enchantmentLevel;

    @Override
    public void decode() {

    }

    @Override
    public void encode() {
        this.reset();
        this.putUnsignedVarInt(this.actionId);
        this.putSlot(gameVersion, this.item);
        this.putVarInt(this.enchantmentId);
        this.putVarInt(this.enchantmentLevel);
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }
}
