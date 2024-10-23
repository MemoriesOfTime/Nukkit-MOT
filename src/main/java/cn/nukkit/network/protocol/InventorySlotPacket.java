package cn.nukkit.network.protocol;

import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.types.inventory.ContainerSlotType;
import cn.nukkit.network.protocol.types.inventory.FullContainerName;
import lombok.ToString;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
@ToString
public class InventorySlotPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.INVENTORY_SLOT_PACKET;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    public int inventoryId;
    public int networkId;
    public int slot;
    public Item item;
    /**
     * @since v712
     */
    public FullContainerName containerNameData = new FullContainerName(ContainerSlotType.ANVIL_INPUT, null);
    /**
     * @since v729
     * @deprecated since v748. Use storageItem ItemData size instead.
     */
    public int dynamicContainerSize;
    /**
     * @since v748
     */
    private Item storageItem;

    @Override
    public void decode() {
        /*this.inventoryId = (int) this.getUnsignedVarInt();
        this.slot = (int) this.getUnsignedVarInt();
        this.item = this.getSlot(this.protocol);*/
    }

    @Override
    public void encode() {
        this.reset();
        this.putUnsignedVarInt(this.inventoryId);
        this.putUnsignedVarInt(this.slot);
        if (this.protocol >= ProtocolInfo.v1_21_30) {
            this.putByte((byte) this.containerNameData.getContainer().getId());
            this.putOptionalNull(this.containerNameData.getDynamicId(), this::putLInt);
            if (this.protocol >= ProtocolInfo.v1_21_40) {
                this.putSlot(this.protocol, this.storageItem);
            } else {
                this.putUnsignedVarInt(this.dynamicContainerSize);
            }
        } else if (this.protocol >= ProtocolInfo.v1_21_20) {
            this.putUnsignedVarInt(this.containerNameData == null || this.containerNameData.getDynamicId() == null ? 0 : this.containerNameData.getDynamicId());
        }
        if (protocol >= 407 && protocol < ProtocolInfo.v1_16_220) {
            this.putVarInt(networkId);
        }
        this.putSlot(protocol, this.item);
    }
}
