package cn.nukkit.network.protocol.v70;

import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ContainerSetSlotPacket  extends DataPacket {
    @Override
    public byte pid() {
        return ProtocolInfo.oldProtocolInfo.get(this.protocol).get(this.getClass());
    }

    public int windowId;

    public int slot;//在背包中的槽位,除去盔甲栏，则为0-35
    public int hotbarSlot;//一直为0？？？

    public Item item;

    @Override
    public void decode() {
        this.windowId = this.getByte();
        if(this.protocol >= ProtocolInfo.v_0_16_0){
            this.slot = this.getVarInt();
            this.hotbarSlot = this.getVarInt();
            this.item = this.getSlotV113(this.protocol);
            return;
        }
        this.slot = this.getShort();
        this.hotbarSlot = this.slot;
        if(this.protocol <= ProtocolInfo.v_0_12_1){
            this.item = this.getSlot_old(this.protocol);
            return;
        }
        this.hotbarSlot = this.getShort();
        this.item = this.getSlot_old(this.protocol);
    }

    @Override
    public void encode() {
        this.tryReset();
        this.putByte((byte) this.windowId);
        if(this.protocol >= ProtocolInfo.v_0_16_0){
            this.putVarInt(this.slot);
            this.putVarInt(this.hotbarSlot);
            this.putSlot_old(this.protocol, this.item);
            return;
        }
        this.putShort(this.slot);
        if(this.protocol <= ProtocolInfo.v_0_12_1){
            this.putSlot_old(this.protocol, this.item);
            return;
        }
        this.putShort(this.hotbarSlot);
        this.putSlot_old(this.protocol, this.item);
    }
}
