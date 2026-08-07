package cn.nukkit.network.protocol.types.inventory.creative;

import cn.nukkit.item.Item;
import lombok.Data;

@Data
public class CreativeItemData {
    private final Item item;
    private final int netId;
    private final int groupId;
}
