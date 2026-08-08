package cn.nukkit.network.protocol;

import cn.nukkit.item.Item;
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
        return NETWORK_ID;
    }

    public long eid;
    public Item item;
    public int inventorySlot;
    public int hotbarSlot;
    public int windowId;

    @Override
    public void decode() {
        if (protocol >= ProtocolInfo.v1_2_0){
            this.eid = this.getEntityRuntimeId();
        }else{
            this.eid = this.getEntityUniqueId();
        }
        this.item = this.getNetworkItemStackDescriptor(this.gameVersion);
        this.inventorySlot = this.getByte();
        this.hotbarSlot = this.getByte();
        this.windowId = this.getByte();
    }

    @Override
    public void encode() {
        this.reset();
        if (protocol >= ProtocolInfo.v1_2_0){
            this.putEntityRuntimeId(this.eid);
        }else{
            this.putEntityUniqueId(this.eid);
        }
        this.putNetworkItemStackDescriptor(gameVersion, this.item);
        this.putByte((byte) this.inventorySlot);
        this.putByte((byte) this.hotbarSlot);
        this.putByte((byte) this.windowId);
    }
}
