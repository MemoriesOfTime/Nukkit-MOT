package cn.nukkit.block.properties;

import cn.nukkit.block.customblock.container.BlockStorageContainer;
import cn.nukkit.item.RuntimeItems;
import cn.nukkit.network.protocol.ProtocolInfo;

public interface BlockPropertiesHelper extends BlockStorageContainer {

    int getId();

    int getDamage();
    void setDamage(int meta);

    @Override
    default int getStorage() {
        return this.getDamage();
    }

    @Override
    default void setStorage(int damage) {
        this.setDamage(damage);
    }

    @Override
    default int getNukkitId() {
        return this.getId();
    }

    default String getIdentifier() {
        return RuntimeItems.getMapping(ProtocolInfo.CURRENT_PROTOCOL).getNamespacedIdByNetworkId(this.getRuntimeId());
    }
}
