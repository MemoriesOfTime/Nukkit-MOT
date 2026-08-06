package cn.nukkit.network.protocol.v70;

import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ContainerSetContentPacket  extends DataPacket {
    @Override
    public byte pid() {
        return ProtocolInfo.oldProtocolInfo.get(this.protocol).get(this.getClass());
    }

    public static final byte SPECIAL_INVENTORY = (byte) 0x0;//玩家的背包？
    public static final byte SPECIAL_ARMOR = (byte) 0x78;//玩家的盔甲栏
    public static final byte SPECIAL_CREATIVE = (byte) 0x79;//创造背包？
    public static final byte SPECIAL_CRAFTING = (byte) 0x7a;//合成表

    public int windowId;
    public Item[] slots = new Item[0];
    public int[] hotbar = new int[0];

    @Override
    public void decode() {
        this.windowId = this.getByte();
        if(this.protocol >= ProtocolInfo.v_0_16_0){
            int count = (int) this.getUnsignedVarInt();
            this.slots = new Item[count];

            for (int s = 0; s < count && !this.feof(); ++s) {
                this.slots[s] = this.getSlotV113(this.protocol);
            }

            if (this.windowId == SPECIAL_INVENTORY) {
                count = (int) this.getUnsignedVarInt();
                this.hotbar = new int[count];
                for (int s = 0; s < count && !this.feof(); ++s) {
                    this.hotbar[s] = this.getVarInt();
                }
            }
            return;
        }
        int count = this.getShort();
        this.slots = new Item[count];
        for (int s = 0; s < count && !this.feof(); ++s) {
            this.slots[s] = this.getSlot_old(this.protocol);
        }
        if (this.windowId == SPECIAL_INVENTORY) {
            count = this.getShort();
            this.hotbar = new int[count];
            for (int s = 0; s < count && !this.feof(); ++s) {
                this.hotbar[s] = this.getInt();
            }
        }
    }

    @Override
    public void encode() {
        this.tryReset();
        this.putByte((byte) this.windowId);
        if(this.protocol >= ProtocolInfo.v_0_16_0){
            this.putUnsignedVarInt(this.slots.length);
            for (Item slot : this.slots) {
                this.putSlot_old(this.protocol, slot);
            }

            if (this.windowId == SPECIAL_INVENTORY && this.hotbar.length > 0) {
                this.putUnsignedVarInt(this.hotbar.length);
                for (int slot : this.hotbar) {
                    this.putVarInt(slot);
                }
            } else {
                this.putUnsignedVarInt(0);
            }
            return;
        }
        this.putShort(this.slots.length);
        for (Item slot : this.slots) {
            this.putSlot_old(this.protocol, slot);
        }
        if (this.windowId == SPECIAL_INVENTORY && this.hotbar.length > 0) {
            this.putShort(this.hotbar.length);
            for (int slot : this.hotbar) {
                this.putInt(slot);
            }
        } else {
            this.putShort(0);
        }
    }
}
