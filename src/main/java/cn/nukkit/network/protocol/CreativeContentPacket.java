package cn.nukkit.network.protocol;

import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.types.inventory.creative.CreativeItemData;
import cn.nukkit.network.protocol.types.inventory.creative.CreativeItemGroup;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.ToString;

import java.util.List;

@ToString
public class CreativeContentPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.CREATIVE_CONTENT_PACKET;

    //TODO
    public Item[] entries;

    /**
     * @since v776 1.21.60
     */
    public final List<CreativeItemGroup> groups = new ObjectArrayList<>();
    /**
     * @since v776 1.21.60
     */
    public final List<CreativeItemData> contents = new ObjectArrayList<>();

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
        if (this.protocol >= ProtocolInfo.v1_21_60) {
            this.putArray(this.groups, this::writeGroup);
            this.putArray(this.contents, this::writeItem);
        } else {
            this.putUnsignedVarInt(entries.length);
            int i = 1; //HACK around since 0 is not indexed by client
            for (Item entry : entries) {
                this.putUnsignedVarInt(i++);
                this.putSlot(protocol, entry, protocol >= ProtocolInfo.v1_16_220);
            }
        }
    }

    private void writeGroup(CreativeItemGroup group) {
        this.putLInt(group.getCategory().ordinal());
        this.putString(group.getName());
        this.putSlot(this.protocol, group.getIcon(), true);
    }

    private void writeItem(CreativeItemData data) {
        this.putUnsignedVarInt(data.getNetId());
        this.putSlot(this.protocol, data.getItem(), true);
        this.putUnsignedVarInt(data.getGroupId());
    }

}
