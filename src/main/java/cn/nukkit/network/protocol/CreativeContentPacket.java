package cn.nukkit.network.protocol;

import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.types.inventory.creative.CreativeItemGroup;
import lombok.ToString;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        this.decodeUnsupported();
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
            Map<Item, CreativeItemGroup> contents = this.creativeItems.getContents(this.gameVersion);
            List<CreativeItemGroup> groups = this.creativeItems.getGroups(this.gameVersion);
            Map<CreativeItemGroup, Integer> groupIdMap = new HashMap<>(groups.size());
            for (int i = 0; i < groups.size(); i++) {
                groupIdMap.put(groups.get(i), i);
            }

            this.putArray(groups, this::writeGroup);
            this.putUnsignedVarInt(contents.size());
            int creativeNetId = 1; // 0 is not indexed by client
            for (Map.Entry<Item, CreativeItemGroup> entry : contents.entrySet()) {
                this.putUnsignedVarInt(creativeNetId++);
                this.putSlot(gameVersion, entry.getKey(), true);
                this.putUnsignedVarInt(entry.getValue() != null ? groupIdMap.getOrDefault(entry.getValue(), 0) : 0);
            }
        } else {
            Collection<Item> items = this.creativeItems.getItems(this.gameVersion);
            this.putUnsignedVarInt(items.size());
            int creativeNetId = 1;
            for (Item entry : items) {
                this.putUnsignedVarInt(creativeNetId++);
                this.putSlot(gameVersion, entry, this.protocol >= ProtocolInfo.v1_16_220);
            }
        }
    }

    private void writeGroup(CreativeItemGroup group) {
        this.putLInt(group.getCategory().ordinal());
        this.putString(group.getName());
        this.putSlot(gameVersion, group.getIcon(), true);
    }

}
