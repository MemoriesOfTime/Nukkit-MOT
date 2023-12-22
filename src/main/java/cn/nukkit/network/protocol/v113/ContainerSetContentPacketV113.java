package cn.nukkit.network.protocol.v113;

import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * author: MagicDroidX
 * Nukkit Project
 */
public class ContainerSetContentPacketV113 extends DataPacket {
    public static final byte NETWORK_ID = ProtocolInfo.CRAFTING_DATA_PACKET;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    public static final byte SPECIAL_INVENTORY = 0;
    public static final byte SPECIAL_ARMOR = 0x78;
    public static final byte SPECIAL_CREATIVE = 0x79;
    public static final byte SPECIAL_HOTBAR = 0x7a;
    public static final byte SPECIAL_FIXED_INVENTORY = 0x7b;

    public long windowid;
    public long eid;
    public Item[] slots = Item.EMPTY_ARRAY;
    public int[] hotbar = new int[0];

    @Override
    public DataPacket clean() {
        this.slots = Item.EMPTY_ARRAY;
        this.hotbar = new int[0];
        return super.clean();
    }

    @Override
    public void decode() {
        this.windowid = (int) this.getUnsignedVarInt();
        this.eid = this.getVarLong();
        int count = (int) this.getUnsignedVarInt();
        this.slots = new Item[count];

        for (int s = 0; s < count && !this.feof(); ++s) {
            this.slots[s] = this.getSlot();
        }

        count = (int) this.getUnsignedVarInt();
        this.hotbar = new int[count];
        for (int s = 0; s < count && !this.feof(); ++s) {
            this.hotbar[s] = this.getVarInt();
        }
    }

    @Override
    public void encode() {
        this.reset();
        this.putUnsignedVarInt(this.windowid);
        this.putVarLong(this.eid);
        this.putUnsignedVarInt(this.slots.length);
        for (Item slot : this.slots) {
            this.putSlot(slot);
        }

        if (this.windowid == SPECIAL_INVENTORY && this.hotbar.length > 0) {
            this.putUnsignedVarInt(this.hotbar.length);
            for (int slot : this.hotbar) {
                this.putVarInt(slot);
            }
        } else {
            this.putUnsignedVarInt(0);
        }
    }

    @Override
    public ContainerSetContentPacketV113 clone() {
        ContainerSetContentPacketV113 pk = (ContainerSetContentPacketV113) super.clone();
        pk.slots = this.slots.clone();
        return pk;
    }
}
