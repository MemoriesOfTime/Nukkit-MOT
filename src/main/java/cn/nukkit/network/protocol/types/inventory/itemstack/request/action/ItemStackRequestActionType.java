package cn.nukkit.network.protocol.types.inventory.itemstack.request.action;

import cn.nukkit.GameVersion;
import cn.nukkit.network.protocol.ProtocolInfo;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;

public enum ItemStackRequestActionType {

    TAKE(0),
    PLACE(1),
    SWAP(2),
    DROP(3),
    DESTROY(4),
    CONSUME(5),
    CREATE(6),
    PLACE_IN_ITEM_CONTAINER(7),
    TAKE_FROM_ITEM_CONTAINER(8),
    LAB_TABLE_COMBINE(9),
    BEACON_PAYMENT(10),
    MINE_BLOCK(11),
    CRAFT_RECIPE(12),
    CRAFT_RECIPE_AUTO(13),
    CRAFT_CREATIVE(14),
    CRAFT_RECIPE_OPTIONAL(15),
    CRAFT_REPAIR_AND_DISENCHANT(16),
    CRAFT_LOOM(17),
    CRAFT_NON_IMPLEMENTED_DEPRECATED(18),
    CRAFT_RESULTS_DEPRECATED(19);

    private final int id;

    private static final Int2ObjectArrayMap<ItemStackRequestActionType> VALUES = new Int2ObjectArrayMap<>();

    static {
        for (var v : values()) {
            VALUES.put(v.getId(), v);
        }
    }

    ItemStackRequestActionType(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static ItemStackRequestActionType fromId(int id) {
        return VALUES.get(id);
    }

    public static ItemStackRequestActionType fromId(int id, GameVersion gameVersion) {
        if (gameVersion.getProtocol() >= ProtocolInfo.v1_18_10_26) {
            return fromId(id);
        }
        int protocol = gameVersion.getProtocol();
        return switch (id) {
            case 0 -> TAKE;
            case 1 -> PLACE;
            case 2 -> SWAP;
            case 3 -> DROP;
            case 4 -> DESTROY;
            case 5 -> CONSUME;
            case 6 -> CREATE;
            case 7 -> LAB_TABLE_COMBINE;
            case 8 -> BEACON_PAYMENT;
            case 9 -> protocol >= ProtocolInfo.v1_16_210 ? MINE_BLOCK : CRAFT_RECIPE;
            case 10 -> protocol >= ProtocolInfo.v1_16_210 ? CRAFT_RECIPE : CRAFT_RECIPE_AUTO;
            case 11 -> protocol >= ProtocolInfo.v1_16_210 ? CRAFT_RECIPE_AUTO : CRAFT_CREATIVE;
            case 12 -> protocol >= ProtocolInfo.v1_16_210 ? CRAFT_CREATIVE
                    : protocol >= ProtocolInfo.v1_16_200 ? CRAFT_RECIPE_OPTIONAL : CRAFT_NON_IMPLEMENTED_DEPRECATED;
            case 13 -> protocol >= ProtocolInfo.v1_16_210 ? CRAFT_RECIPE_OPTIONAL
                    : protocol >= ProtocolInfo.v1_16_200 ? CRAFT_NON_IMPLEMENTED_DEPRECATED : CRAFT_RESULTS_DEPRECATED;
            case 14 -> protocol >= ProtocolInfo.v1_17_40 ? CRAFT_REPAIR_AND_DISENCHANT
                    : protocol >= ProtocolInfo.v1_16_210 ? CRAFT_NON_IMPLEMENTED_DEPRECATED
                    : protocol >= ProtocolInfo.v1_16_200 ? CRAFT_RESULTS_DEPRECATED : null;
            case 15 -> protocol >= ProtocolInfo.v1_17_40 ? CRAFT_LOOM
                    : protocol >= ProtocolInfo.v1_16_210 ? CRAFT_RESULTS_DEPRECATED : null;
            case 16 -> protocol >= ProtocolInfo.v1_17_40 ? CRAFT_NON_IMPLEMENTED_DEPRECATED : null;
            case 17 -> protocol >= ProtocolInfo.v1_17_40 ? CRAFT_RESULTS_DEPRECATED : null;
            default -> null;
        };
    }
}
