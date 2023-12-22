package cn.nukkit.network.protocol.v113;

import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.DataPacket;

public class InventoryActionPacketV113 extends DataPacket {

    public static final byte NETWORK_ID = 0x2f;

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
        this.putSlot(this.item);
        this.putVarInt(this.enchantmentId);
        this.putVarInt(this.enchantmentLevel);
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }
}
