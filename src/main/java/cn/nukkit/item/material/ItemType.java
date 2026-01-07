package cn.nukkit.item.material;

import cn.nukkit.block.Block;
import cn.nukkit.item.Item;
import cn.nukkit.item.RuntimeItems;
import cn.nukkit.network.protocol.ProtocolInfo;

public interface ItemType {

    String getIdentifier();

    int getRuntimeId();

    default Item createItem() {
        return this.createItem(1);
    }

    default Item createItem(int count) {
        return this.createItem(count, 0);
    }

    default Item createItem(int count, int meta) {
        try {
            var mapping = RuntimeItems.getMapping(ProtocolInfo.CURRENT_PROTOCOL);
            var entry = mapping.fromRuntime(this.getRuntimeId());
            int damage = entry.isHasDamage() ? entry.getDamage() : meta;
            return Item.get(entry.getLegacyId(), damage, count);
        } catch (IllegalArgumentException e) {
            return Item.get(Block.AIR);
        }
    }
}
