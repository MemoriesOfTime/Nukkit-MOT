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
public class InventoryContentPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.INVENTORY_CONTENT_PACKET;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    public static final int SPECIAL_INVENTORY = 0;
    public static final int SPECIAL_OFFHAND = 0x77;
    public static final int SPECIAL_ARMOR = 0x78;
    public static final int SPECIAL_CREATIVE = 0x79;
    public static final int SPECIAL_HOTBAR = 0x7a;
    public static final int SPECIAL_FIXED_INVENTORY = 0x7b;

    public int inventoryId;
    public int networkId;
    public Item[] slots = Item.EMPTY_ARRAY;
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
    public Item storageItem;

    @Override
    public DataPacket clean() {
        this.slots = Item.EMPTY_ARRAY;
        return super.clean();
    }

    @Override
    public void decode() {
        /*this.inventoryId = (int) this.getUnsignedVarInt();
        int count = (int) this.getUnsignedVarInt();
        this.slots = new Item[count];

        for (int s = 0; s < count && !this.feof(); ++s) {
            this.slots[s] = this.getSlot();
        }*/
    }

    @Override
    public void encode() {
        this.reset();
        this.putUnsignedVarInt(this.inventoryId);
        this.putUnsignedVarInt(this.slots.length);
        for (Item slot : this.slots) {
            if (protocol >= 407 && protocol < ProtocolInfo.v1_16_220) {
                this.putVarInt(networkId);
            }
            this.putSlot(gameVersion, slot);
        }
        if (this.protocol >= ProtocolInfo.v1_21_30) {
            this.putByte((byte) this.containerNameData.getContainer().getId());
            this.putOptionalNull(this.containerNameData.getDynamicId(), this::putLInt);
            if (this.protocol >= ProtocolInfo.v1_21_40) {
                this.putSlot(gameVersion, this.storageItem);
            } else {
                this.putUnsignedVarInt(this.dynamicContainerSize);
            }
        } else if (this.protocol >= ProtocolInfo.v1_21_20) {
            this.putUnsignedVarInt(this.containerNameData == null || this.containerNameData.getDynamicId() == null ? 0 : this.containerNameData.getDynamicId());
        }
    }

    @Override
    public InventoryContentPacket clone() {
        InventoryContentPacket pk = (InventoryContentPacket) super.clone();
        pk.slots = this.slots.clone();
        return pk;
    }
}
