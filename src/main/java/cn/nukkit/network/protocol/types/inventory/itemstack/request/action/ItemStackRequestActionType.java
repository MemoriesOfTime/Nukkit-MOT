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
        int protocol = gameVersion.getProtocol();
        if (protocol >= ProtocolInfo.v1_21_20) {
            return switch (id) {
                case 0 -> TAKE;
                case 1 -> PLACE;
                case 2 -> SWAP;
                case 3 -> DROP;
                case 4 -> DESTROY;
                case 5 -> CONSUME;
                case 6 -> CREATE;
                case 9 -> LAB_TABLE_COMBINE;
                case 10 -> BEACON_PAYMENT;
                case 11 -> MINE_BLOCK;
                case 12 -> CRAFT_RECIPE;
                case 13 -> CRAFT_RECIPE_AUTO;
                case 14 -> CRAFT_CREATIVE;
                case 15 -> CRAFT_RECIPE_OPTIONAL;
                case 16 -> CRAFT_REPAIR_AND_DISENCHANT;
                case 17 -> CRAFT_LOOM;
                case 18 -> CRAFT_NON_IMPLEMENTED_DEPRECATED;
                case 19 -> CRAFT_RESULTS_DEPRECATED;
                default -> null;
            };
        }
        if (protocol >= ProtocolInfo.v1_18_10_26) {
            return fromId(id);
        }
        if (protocol >= ProtocolInfo.v1_17_40) {
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
                case 9 -> MINE_BLOCK;
                case 10 -> CRAFT_RECIPE;
                case 11 -> CRAFT_RECIPE_AUTO;
                case 12 -> CRAFT_CREATIVE;
                case 13 -> CRAFT_RECIPE_OPTIONAL;
                case 14 -> CRAFT_REPAIR_AND_DISENCHANT;
                case 15 -> CRAFT_LOOM;
                case 16 -> CRAFT_NON_IMPLEMENTED_DEPRECATED;
                case 17 -> CRAFT_RESULTS_DEPRECATED;
                default -> null;
            };
        }
        if (protocol >= ProtocolInfo.v1_16_210) {
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
                case 9 -> MINE_BLOCK;
                case 10 -> CRAFT_RECIPE;
                case 11 -> CRAFT_RECIPE_AUTO;
                case 12 -> CRAFT_CREATIVE;
                case 13 -> CRAFT_RECIPE_OPTIONAL;
                case 14 -> CRAFT_NON_IMPLEMENTED_DEPRECATED;
                case 15 -> CRAFT_RESULTS_DEPRECATED;
                default -> null;
            };
        }
        if (protocol >= ProtocolInfo.v1_16_200) {
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
                case 9 -> CRAFT_RECIPE;
                case 10 -> CRAFT_RECIPE_AUTO;
                case 11 -> CRAFT_CREATIVE;
                case 12 -> CRAFT_RECIPE_OPTIONAL;
                case 13 -> CRAFT_NON_IMPLEMENTED_DEPRECATED;
                case 14 -> CRAFT_RESULTS_DEPRECATED;
                default -> null;
            };
        }
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
            case 9 -> CRAFT_RECIPE;
            case 10 -> CRAFT_RECIPE_AUTO;
            case 11 -> CRAFT_CREATIVE;
            case 12 -> CRAFT_NON_IMPLEMENTED_DEPRECATED;
            case 13 -> CRAFT_RESULTS_DEPRECATED;
            default -> null;
        };
    }
}
