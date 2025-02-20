package cn.nukkit.network.protocol;

import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.types.inventory.creative.CreativeItemData;
import cn.nukkit.network.protocol.types.inventory.creative.CreativeItemGroup;
import lombok.ToString;

@ToString
public class CreativeContentPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.CREATIVE_CONTENT_PACKET;

    @Deprecated
    public Item[] entries;

    public Item.CreativeItems creativeItems;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
    }

    @Override
    public void encode() {
        this.reset();

        if (this.creativeItems == null) {
            if (this.entries != null) {
                this.creativeItems = new Item.CreativeItems();
                for (Item item : this.entries) {
                    this.creativeItems.add(item);
                }
            } else { // Spectator
                if (this.protocol >= ProtocolInfo.v1_21_60) {
                    this.putUnsignedVarInt(0); // group count
                }
                this.putUnsignedVarInt(0); // item count
                return;
            }
        }

        if (this.protocol >= ProtocolInfo.v1_21_60) {
            this.putArray(this.creativeItems.getGroups(), this::writeGroup);
        }
        this.putArray(this.creativeItems.getCreativeItemDatas(), this::writeItem);
    }

    private void writeGroup(CreativeItemGroup group) {
        this.putLInt(group.getCategory().ordinal());
        this.putString(group.getName());
        this.putSlot(this.protocol, group.getIcon(), true);
    }

    private void writeItem(CreativeItemData data) {
        this.putUnsignedVarInt(data.getNetId());
        this.putSlot(this.protocol, data.getItem(), this.protocol >= ProtocolInfo.v1_16_220);
        if (this.protocol >= ProtocolInfo.v1_21_60) {
            this.putUnsignedVarInt(data.getGroupId());
        }
    }

}
