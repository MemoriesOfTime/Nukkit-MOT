package cn.nukkit.network.protocol.types.inventory.creative;

import cn.nukkit.item.Item;
import lombok.Data;

@Data
public class CreativeItemGroup {
    private final CreativeItemCategory category;
    private final String name;
    private final Item icon;
}
