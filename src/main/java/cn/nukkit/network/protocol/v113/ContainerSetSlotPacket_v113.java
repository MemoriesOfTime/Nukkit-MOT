package cn.nukkit.network.protocol.v113;

import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.v70.ContainerSetSlotPacket;

/**
 * author: MagicDroidX
 * Nukkit Project
 */
public class ContainerSetSlotPacket_v113 extends DataPacket_v113 {
    public static final byte NETWORK_ID = ProtocolInfo_v113.CONTAINER_SET_SLOT_PACKET;

    @Override
    public byte pid() {
        if(this.protocol < ProtocolInfo.v_1_0_0){
            return ProtocolInfo.oldProtocolInfo.get(this.protocol).get(ContainerSetSlotPacket.class);
        }
        return NETWORK_ID;
    }

    public int windowid;
    public int slot;
    public int hotbarSlot;
    public Item item;
    public int selectedSlot;

    @Override
    public void decode() {
        if(this.protocol <= ProtocolInfo.v_0_15_10){
            this.windowid = this.getByte();
            this.slot = this.getShort();
            this.hotbarSlot = this.slot;
            if(this.protocol <= ProtocolInfo.v_0_12_1){
                this.item = this.getSlot_old(this.protocol);
                return;
            }
            this.hotbarSlot = this.getShort();
            this.item = this.getSlot_old(this.protocol);
            return;
        }
        this.windowid = this.getByte();
        this.slot = this.getVarInt();
        this.hotbarSlot = this.getVarInt();
        this.item = this.getSlot(gameVersion);
        if(this.protocol < ProtocolInfo.v_1_0_0){
            return;
        }
        this.selectedSlot = this.getByte();
    }

    @Override
    public void encode() {
        if(this.protocol <= ProtocolInfo.v_1_0_0){
            this.tryReset();
            if(this.protocol >= ProtocolInfo.v_0_16_0){
                this.putByte((byte) this.windowid);
                this.putVarInt(this.slot);
                this.putVarInt(this.hotbarSlot);
                this.putSlot(this.protocol, this.item);
                return;
            }
            this.putByte((byte) this.windowid);
            this.putShort(this.slot);
            if(this.protocol <= ProtocolInfo.v_0_12_1){
                this.putSlot_old(this.protocol, this.item);
                return;
            }
            this.putShort(this.hotbarSlot);
            this.putSlot_old(this.protocol, this.item);

            return;
        }
        this.reset();
        this.putByte((byte) this.windowid);
        this.putVarInt(this.slot);
        this.putVarInt(this.hotbarSlot);
        this.putSlot(gameVersion, this.item);
        this.putByte((byte) this.selectedSlot);
    }
}
