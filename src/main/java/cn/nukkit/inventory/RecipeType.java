package cn.nukkit.inventory;

import cn.nukkit.network.protocol.ProtocolInfo;

public enum RecipeType {

    SHAPELESS(0),
    SHAPED(1),
    FURNACE(2),
    FURNACE_DATA(3),
    BLAST_FURNACE(2),
    BLAST_FURNACE_DATA(3),
    MULTI(4),
    SHULKER_BOX(5),
    SHAPELESS_CHEMISTRY(6),
    SHAPED_CHEMISTRY(7),
    SMITHING_TRANSFORM(8),
    /**
     * @since v582
     */
    SMITHING_TRIM(9),
    REPAIR(-1),
    CAMPFIRE(2),
    CAMPFIRE_DATA(3);

    private final int networkType;

    RecipeType(int networkType) {
        this.networkType = networkType;
    }

    public int getNetworkType(int protocol) {
        return switch (this) {
            case SMITHING_TRANSFORM -> protocol >= ProtocolInfo.v1_19_60 ? networkType : 0;
            case SMITHING_TRIM -> protocol >= ProtocolInfo.v1_19_80 ? networkType : 0;
            default -> networkType;
        };
    }
}
