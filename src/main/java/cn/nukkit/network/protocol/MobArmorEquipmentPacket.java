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

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    public long eid;
    public Item[] slots = new Item[4];
    /**
     * @since v712
     */
    public Item body;

    @Override
    public void decode() {
        this.eid = this.getEntityRuntimeId();
        this.slots = new Item[4];
        this.slots[0] = this.getSlot(this.protocol);
        this.slots[1] = this.getSlot(this.protocol);
        this.slots[2] = this.getSlot(this.protocol);
        this.slots[3] = this.getSlot(this.protocol);
        if (this.protocol >= ProtocolInfo.v1_21_20) {
            this.body = this.getSlot(this.protocol);
        }
    }

    @Override
    public void encode() {
        this.reset();
        this.putEntityRuntimeId(this.eid);
        this.putSlot(protocol, this.slots[0]);
        this.putSlot(protocol, this.slots[1]);
        this.putSlot(protocol, this.slots[2]);
        this.putSlot(protocol, this.slots[3]);
        if (this.protocol >= ProtocolInfo.v1_21_20) {
            this.putSlot(protocol, this.body);
        }
    }
}
