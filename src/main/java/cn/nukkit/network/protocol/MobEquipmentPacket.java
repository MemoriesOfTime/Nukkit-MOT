package cn.nukkit.network.protocol;

import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.v113.ProtocolInfoV113;
import lombok.ToString;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
@ToString
public class MobEquipmentPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.MOB_EQUIPMENT_PACKET;

    @Override
    public byte pid() {
        if(this.protocol >= ProtocolInfo.v1_2_0){
            return NETWORK_ID;
        }else if(this.protocol < ProtocolInfo.v_1_0_0){
            return ProtocolInfo.oldProtocolInfo.get(this.protocol).get(this.getClass());
        }else if(this.protocol < ProtocolInfo.v1_2_0){
            return ProtocolInfoV113.MOB_EQUIPMENT_PACKET;
        }
        return NETWORK_ID;
    }

    public long eid;
    public Item item;
    public int meta;
    public int inventorySlot;// slot
    public int hotbarSlot;// selectedHotbarIndex
    public int windowId;

    @Override
    public void decode() {
        if(this.protocol <= ProtocolInfo.v_0_15_10){
            if(this.protocol > ProtocolInfo.v_0_10_0){
                this.eid = this.getLong();
            }else {
                this.eid = this.getInt();
            }
            if(this.protocol <= ProtocolInfo.v_0_11_0) {
                this.item= Item.get(this.getShort());
                this.meta = this.getShort();
                this.item.setDamage(this.meta);
            }else {
                this.item = this.getSlot_old(this.protocol);
            }
            this.inventorySlot = this.getByte();// slot
            if(this.protocol < ProtocolInfo.v_0_10_0){
                return;
            }
            this.hotbarSlot = this.getByte();// selectedHotbarIndex
            return;
        }
        if(this.protocol >= ProtocolInfo.v1_2_0){
            this.eid = this.getEntityRuntimeId();
        }else{
            this.eid = this.getEntityUniqueId();
        }
        this.item = this.getSlot(this.protocol);
        this.inventorySlot = this.getByte();
        this.hotbarSlot = this.getByte();
        if(this.protocol < ProtocolInfo.v_1_0_0){
            return;
        }
        this.windowId = this.getByte();
    }

    @Override
    public void encode() {
        if(this.protocol < ProtocolInfo.v_1_0_0){
            this.tryReset();
            if(this.protocol >= ProtocolInfo.v_0_16_0){
                this.putEntityRuntimeId(this.eid); //EntityRuntimeID
                this.putSlot(protocol, this.item);
                this.putByte((byte) this.inventorySlot);
                this.putByte((byte) this.hotbarSlot);
                return;
            }
            if(this.protocol > ProtocolInfo.v_0_10_0){
                this.putLong(this.eid);
            }else{
                this.putInt((int) this.eid);
            }
            if(this.protocol <= ProtocolInfo.v_0_11_0) {
                this.putShort(this.item.getId());
                this.putShort(this.item.getDamage());
            }else {
                this.putSlot_old(this.protocol, this.item);
            }
            this.putByte((byte) this.inventorySlot);
            if(this.protocol < ProtocolInfo.v_0_10_0){
                return;
            }
            this.putByte((byte) this.hotbarSlot);
            return;
        }
        this.reset();
        if(this.protocol >= ProtocolInfo.v1_2_0){
            this.putEntityRuntimeId(this.eid);
        }else{
            this.putEntityUniqueId(this.eid);
        }
        this.putSlot(protocol, this.item);
        this.putByte((byte) this.inventorySlot);
        this.putByte((byte) this.hotbarSlot);
        this.putByte((byte) this.windowId);
    }
}
