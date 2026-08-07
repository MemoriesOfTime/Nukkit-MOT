package cn.nukkit.network.protocol;

import cn.nukkit.item.Item;
import lombok.ToString;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
@ToString
public class MobArmorEquipmentPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.MOB_ARMOR_EQUIPMENT_PACKET;

    public long eid;
    public Item[] slots = new Item[4];
    /**
     * @since v712
     */
    public Item body = Item.AIR_ITEM;

    @Override
    public byte pid() {
        if(this.protocol >= ProtocolInfo.v1_2_0){
            return NETWORK_ID;
        }else if(this.protocol < ProtocolInfo.v_1_0_0){
            return ProtocolInfo.oldProtocolInfo.get(this.protocol).get(this.getClass());
        }
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        if(this.protocol <= ProtocolInfo.v_0_15_10){
            if(this.protocol > ProtocolInfo.v_0_10_0){
                this.eid = this.getLong();
            }else{
                this.eid = this.getInt();
            }
            this.slots = new Item[4];
            if(this.protocol <= ProtocolInfo.v_0_11_0){
                this.slots[0] = Item.get(this.getByte());
                this.slots[1] = Item.get(this.getByte());
                this.slots[2] = Item.get(this.getByte());
                this.slots[3] = Item.get(this.getByte());
                return;
            }
            this.slots[0] = this.getSlot_old(this.protocol);
            this.slots[1] = this.getSlot_old(this.protocol);
            this.slots[2] = this.getSlot_old(this.protocol);
            this.slots[3] = this.getSlot_old(this.protocol);
            return;
        }
        if(this.protocol >= ProtocolInfo.v1_2_0){
            this.eid = this.getEntityRuntimeId();
        }else{
            this.eid = this.getEntityUniqueId();
        }
        this.slots = new Item[4];
        this.slots[0] = this.protocol >= ProtocolInfo.v1_26_30 ? this.getNetworkItemStackDescriptor(this.gameVersion) : this.getSlot(this.gameVersion);
        this.slots[1] = this.protocol >= ProtocolInfo.v1_26_30 ? this.getNetworkItemStackDescriptor(this.gameVersion) : this.getSlot(this.gameVersion);
        this.slots[2] = this.protocol >= ProtocolInfo.v1_26_30 ? this.getNetworkItemStackDescriptor(this.gameVersion) : this.getSlot(this.gameVersion);
        this.slots[3] = this.protocol >= ProtocolInfo.v1_26_30 ? this.getNetworkItemStackDescriptor(this.gameVersion) : this.getSlot(this.gameVersion);
        if (this.protocol >= ProtocolInfo.v1_21_20) {
            this.body = this.protocol >= ProtocolInfo.v1_26_30 ? this.getNetworkItemStackDescriptor(this.gameVersion) : this.getSlot(this.gameVersion);
        }
    }

    @Override
    public void encode() {
        if(this.protocol <= ProtocolInfo.v_0_15_10){
            this.tryReset();
            if(this.protocol > ProtocolInfo.v_0_10_0){
                this.putLong(this.eid);
            }else{
                this.putInt((int) this.eid);
            }
            if(this.protocol <= ProtocolInfo.v_0_11_0) { //Waiting for convert to byte
                this.putByte((byte) this.slots[0].getId());
                this.putByte((byte) this.slots[1].getId());
                this.putByte((byte) this.slots[2].getId());
                this.putByte((byte) this.slots[3].getId());
                return;
            }
            this.putSlot_old(this.protocol, this.slots[0]);
            this.putSlot_old(this.protocol, this.slots[1]);
            this.putSlot_old(this.protocol, this.slots[2]);
            this.putSlot_old(this.protocol, this.slots[3]);
            return;
        }
        this.reset();
        this.putEntityRuntimeId(this.eid);
        if (this.protocol >= ProtocolInfo.v1_26_30) {
            this.putNetworkItemStackDescriptor(this.gameVersion, this.slots[0]);
            this.putNetworkItemStackDescriptor(this.gameVersion, this.slots[1]);
            this.putNetworkItemStackDescriptor(this.gameVersion, this.slots[2]);
            this.putNetworkItemStackDescriptor(this.gameVersion, this.slots[3]);
        } else {
            this.putSlot(this.gameVersion, this.slots[0]);
            this.putSlot(this.gameVersion, this.slots[1]);
            this.putSlot(this.gameVersion, this.slots[2]);
            this.putSlot(this.gameVersion, this.slots[3]);
        }
        if (this.protocol >= ProtocolInfo.v1_21_20) {
            if (this.protocol >= ProtocolInfo.v1_26_30) {
                this.putNetworkItemStackDescriptor(this.gameVersion, this.body);
            } else {
                this.putSlot(this.gameVersion, this.body);
            }
        }
    }
}
