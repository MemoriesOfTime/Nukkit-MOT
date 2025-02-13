package cn.nukkit.network.protocol.types.inventory.creative;

import cn.nukkit.item.Item;
import lombok.Data;

@Data
public class CreativeItemGroup {
    public final int groupId;
    public final CreativeItemCategory category;
    public final String name;
    public final Item icon;
}
