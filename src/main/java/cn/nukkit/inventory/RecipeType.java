package cn.nukkit.inventory;

import cn.nukkit.network.protocol.ProtocolInfo;

public enum RecipeType {

    SHAPELESS(0),
    SHAPED(1),
    FURNACE(2),
    FURNACE_DATA(3),
    MULTI(4),
    SHULKER_BOX(5),
    SHAPELESS_CHEMISTRY(6),
    SHAPED_CHEMISTRY(7),
    SMITHING_TRANSFORM(8),
    REPAIR(-1),
    CAMPFIRE(2),
    CAMPFIRE_DATA(3);

    private final int networkType;

    RecipeType(int networkType) {
        this.networkType = networkType;
    }

    public int getNetworkType(int protocol) {
        if (this == SMITHING_TRANSFORM) {
            return protocol >= ProtocolInfo.v1_19_60 ? networkType : 0;
        }
        return networkType;
    }
}
