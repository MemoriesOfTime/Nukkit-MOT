package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.Server;
import cn.nukkit.network.protocol.ProtocolInfo;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Log4j2
@UtilityClass
public class RuntimeItems {

    private static final Map<String, Integer> legacyString2LegacyInt = new HashMap<>();
    private static final Map<String, int[]> flattenedId2Legacy = new HashMap<>();

    private static Map<String, MappingEntry> mappingEntries = new HashMap<>();

    /**
     * 按锚点 GameVersion 懒构建的运行时物品映射，没有客户端连接过的协议不占常驻内存。
     * <p>
     * Runtime item mappings built lazily per anchor GameVersion; protocols with no
     * client connection never materialize.
     */
    private static final Map<GameVersion, RuntimeItemMapping> MAPPINGS = new ConcurrentHashMap<>();

    private static RuntimeItemMapping[] allMappings;

    private static boolean initialized;

    private static RuntimeItemMapping of(GameVersion anchor) {
        return MAPPINGS.computeIfAbsent(anchor, gv -> new RuntimeItemMapping(mappingEntries, gv));
    }

    /**
     * 物化全部协议的映射并返回（原 VALUES 字段的懒加载替代，顺序一致）。
     * <p>
     * Materializes mappings for every protocol and returns them (lazy replacement
     * for the old VALUES field, same order).
     */
    public static RuntimeItemMapping[] values() {
        if (allMappings == null) {
            allMappings = new RuntimeItemMapping[]{
                    of(GameVersion.V1_12_0),
                    of(GameVersion.V1_16_100),
                    of(GameVersion.V1_17_0),
                    of(GameVersion.V1_17_10),
                    of(GameVersion.V1_18_0),
                    of(GameVersion.V1_18_10),
                    of(GameVersion.V1_18_30),
                    of(GameVersion.V1_19_0),
                    of(GameVersion.V1_19_10),
                    of(GameVersion.V1_19_50),
                    of(GameVersion.V1_19_60),
                    of(GameVersion.V1_19_70),
                    of(GameVersion.V1_19_80),
                    of(GameVersion.V1_20_0),
                    of(GameVersion.V1_20_10),
                    of(GameVersion.V1_20_30),
                    of(GameVersion.V1_20_50),
                    of(GameVersion.V1_20_60),
                    of(GameVersion.V1_20_70),
                    of(GameVersion.V1_20_80),
                    of(GameVersion.V1_21_0),
                    of(GameVersion.V1_21_20),
                    of(GameVersion.V1_21_30),
                    of(GameVersion.V1_21_40),
                    of(GameVersion.V1_21_50),
                    of(GameVersion.V1_21_60),
                    of(GameVersion.V1_21_70),
                    of(GameVersion.V1_21_80),
                    of(GameVersion.V1_21_90),
                    of(GameVersion.V1_21_93),
                    of(GameVersion.V1_21_100),
                    of(GameVersion.V1_21_111),
                    of(GameVersion.V1_21_120),
                    of(GameVersion.V1_21_130),
                    of(GameVersion.V1_26_0),
                    of(GameVersion.V1_26_10),
                    of(GameVersion.V1_26_20),
                    of(GameVersion.V1_26_30),
                    of(GameVersion.V1_26_40),
                    // NetEase
                    of(GameVersion.V1_20_50_NETEASE),
                    of(GameVersion.V1_21_2_NETEASE),
                    of(GameVersion.V1_21_50_NETEASE),
                    of(GameVersion.V1_21_93_NETEASE),
                    of(GameVersion.V1_21_124_NETEASE)
            };
        }
        return allMappings;
    }

    public static void init() {
        if (initialized) {
            throw new IllegalStateException("RuntimeItems were already generated!");
        }
        initialized = true;
        log.debug("Loading runtime items...");
        InputStream itemIdsStream = Server.class.getClassLoader().getResourceAsStream("legacy_item_ids.json");
        if (itemIdsStream == null) {
            throw new AssertionError("Unable to load legacy_item_ids.json");
        }

        JsonObject json = JsonParser.parseReader(new InputStreamReader(itemIdsStream)).getAsJsonObject();
        for (String identifier : json.keySet()) {
            legacyString2LegacyInt.put(identifier, json.get(identifier).getAsInt());
        }

        InputStream mappingStream = Server.class.getClassLoader().getResourceAsStream("item_mappings.json");
        if (mappingStream == null) {
            throw new AssertionError("Unable to load item_mappings.json");
        }
        JsonObject itemMapping = JsonParser.parseReader(new InputStreamReader(mappingStream)).getAsJsonObject();

        for (String legacyName : itemMapping.keySet()) {
            JsonObject convertData = itemMapping.getAsJsonObject(legacyName);
            int protocol = 0;
            try {
                protocol = convertData.get("protocol").getAsInt();
            } catch (Exception ignored) {

            }
            for (String key : convertData.keySet()) {
                if ("protocol".equalsIgnoreCase(key)) {
                    continue;
                }
                String identifier = convertData.get(key).getAsString();
                int damage = Integer.parseInt(key);
                mappingEntries.put(identifier, new MappingEntry(legacyName, damage, protocol));
            }
        }

        // Register flattened identifiers (e.g., minecraft:oak_log) to legacy ID + damage
        for (Map.Entry<String, MappingEntry> entry : mappingEntries.entrySet()) {
            String flattenedId = entry.getKey();
            if (flattenedId.isEmpty()) {
                continue;
            }
            MappingEntry mappingEntry = entry.getValue();
            int legacyId = legacyString2LegacyInt.getOrDefault(mappingEntry.getLegacyName(), -1);
            if (legacyId != -1 && !flattenedId2Legacy.containsKey(flattenedId)) {
                flattenedId2Legacy.put(flattenedId, new int[]{legacyId, mappingEntry.getDamage()});
            }
        }

    }

    @Deprecated
    public static RuntimeItemMapping getMapping(int protocolId) {
        return getMapping(GameVersion.byProtocol(protocolId, Server.getInstance().onlyNetEaseMode));
    }

    public static RuntimeItemMapping getMapping(GameVersion gameVersion) {
        int protocolId = gameVersion.getProtocol();
        if (gameVersion.isNetEase()) {
            return getMappingNetEase(protocolId);
        }
        if (protocolId >= ProtocolInfo.v1_26_40) {
            return of(GameVersion.V1_26_40);
        } else if (protocolId >= ProtocolInfo.v1_26_30) {
            return of(GameVersion.V1_26_30);
        } else if (protocolId >= ProtocolInfo.v1_26_20_26) {
            return of(GameVersion.V1_26_20);
        } else if (protocolId >= ProtocolInfo.v1_26_10) {
            return of(GameVersion.V1_26_10);
        } else if (protocolId >= ProtocolInfo.v1_26_0) {
            return of(GameVersion.V1_26_0);
        } else if (protocolId >= ProtocolInfo.v1_21_130_28) {
            return of(GameVersion.V1_21_130);
        } else if (protocolId >= ProtocolInfo.v1_21_120) {
            return of(GameVersion.V1_21_120);
        } else if (protocolId >= ProtocolInfo.v1_21_110_26) {
            return of(GameVersion.V1_21_111);
        } else if (protocolId >= ProtocolInfo.v1_21_100) {
            return of(GameVersion.V1_21_100);
        } else if (protocolId >= ProtocolInfo.v1_21_93) {
            return of(GameVersion.V1_21_93);
        } else if (protocolId >= ProtocolInfo.v1_21_90) {
            return of(GameVersion.V1_21_90);
        } else if (protocolId >= ProtocolInfo.v1_21_80) {
            return of(GameVersion.V1_21_80);
        } else if (protocolId >= ProtocolInfo.v1_21_70_24) {
            return of(GameVersion.V1_21_70);
        } else if (protocolId >= ProtocolInfo.v1_21_60) {
            return of(GameVersion.V1_21_60);
        } else if (protocolId >= ProtocolInfo.v1_21_50_26) {
            return of(GameVersion.V1_21_50);
        } else if (protocolId >= ProtocolInfo.v1_21_40) {
            return of(GameVersion.V1_21_40);
        } else if (protocolId >= ProtocolInfo.v1_21_30) {
            return of(GameVersion.V1_21_30);
        } else if (protocolId >= ProtocolInfo.v1_21_20) {
            return of(GameVersion.V1_21_20);
        } else if (protocolId >= ProtocolInfo.v1_21_0) {
            return of(GameVersion.V1_21_0);
        } else if (protocolId >= ProtocolInfo.v1_20_80) {
            return of(GameVersion.V1_20_80);
        } else if (protocolId >= ProtocolInfo.v1_20_70) {
            return of(GameVersion.V1_20_70);
        } else if (protocolId >= ProtocolInfo.v1_20_60) {
            return of(GameVersion.V1_20_60);
        } else if (protocolId >= ProtocolInfo.v1_20_50) {
            return of(GameVersion.V1_20_50);
        } else if (protocolId >= ProtocolInfo.v1_20_30_24) {
            return of(GameVersion.V1_20_30);
        } else if (protocolId >= ProtocolInfo.v1_20_10_21) {
            return of(GameVersion.V1_20_10);
        } else if (protocolId >= ProtocolInfo.v1_20_0_23) {
            return of(GameVersion.V1_20_0);
        } else if (protocolId >= ProtocolInfo.v1_19_80) {
            return of(GameVersion.V1_19_80);
        } else if (protocolId >= ProtocolInfo.v1_19_70_24) {
            return of(GameVersion.V1_19_70);
        } else if (protocolId >= ProtocolInfo.v1_19_60) {
            return of(GameVersion.V1_19_60);
        } else if (protocolId >= ProtocolInfo.v1_19_50_20) {
            return of(GameVersion.V1_19_50);
        } else if (protocolId >= ProtocolInfo.v1_19_10) {
            return of(GameVersion.V1_19_10);
        } else if (protocolId >= ProtocolInfo.v1_19_0_29) {
            return of(GameVersion.V1_19_0);
        } else if (protocolId >= ProtocolInfo.v1_18_30) {
            return of(GameVersion.V1_18_30);
        } else if (protocolId >= ProtocolInfo.v1_18_10_26) {
            return of(GameVersion.V1_18_10);
        } else if (protocolId >= ProtocolInfo.v1_18_0) {
            return of(GameVersion.V1_18_0);
        } else if (protocolId >= ProtocolInfo.v1_17_10) {
            return of(GameVersion.V1_17_10);
        } else if (protocolId >= ProtocolInfo.v1_17_0) {
            return of(GameVersion.V1_17_0);
        } else if (protocolId >= ProtocolInfo.v1_16_100) {
            return of(GameVersion.V1_16_100);
        }
        return of(GameVersion.V1_12_0);
    }

    private static RuntimeItemMapping getMappingNetEase(int protocolId) {
        if (protocolId >= GameVersion.V1_21_124_NETEASE.getProtocol()) {
            return of(GameVersion.V1_21_124_NETEASE);
        } else if (protocolId >= GameVersion.V1_21_93_NETEASE.getProtocol()) {
            return of(GameVersion.V1_21_93_NETEASE);
        } else if (protocolId >= GameVersion.V1_21_50_NETEASE.getProtocol()) {
            return of(GameVersion.V1_21_50_NETEASE);
        } else if (protocolId >= GameVersion.V1_21_2_NETEASE.getProtocol()) {
            return of(GameVersion.V1_21_2_NETEASE);
        }
        return of(GameVersion.V1_20_50_NETEASE);
    }

    public static int getLegacyIdFromLegacyString(String identifier) {
        return legacyString2LegacyInt.getOrDefault(identifier, -1);
    }

    /**
     * @return int[]{legacyId, damage} or null if not found
     */
    public static int[] getLegacyFromFlattenedId(String identifier) {
        return flattenedId2Legacy.get(identifier);
    }

    /**
     * 注册自定义方块的标识符到旧ID的映射
     * Register a custom block's identifier to legacy ID mapping
     * <p>
     * 此方法使Item.fromString()能够识别和创建自定义方块物品
     * This is needed for Item.fromString() to work with custom blocks
     *
     * @param identifier 自定义方块的标识符 / custom block's identifier
     * @param legacyId 旧物品ID / legacy item ID
     */
    public static void registerCustomBlockLegacyId(String identifier, int legacyId) {
        legacyString2LegacyInt.put(identifier, legacyId);
    }

    @Data
    public static class MappingEntry {
        private final String legacyName;
        private final int damage;
        private final int protocol;
    }

    public static int getId(int fullId) {
        return (short) (fullId >> 16);
    }

    public static int getData(int fullId) {
        return ((fullId >> 1) & 0x7fff);
    }

    public static int getFullId(int id, int data) {
        return (((short) id) << 16) | ((data & 0x7fff) << 1);
    }

    public static int getNetworkId(int networkFullId) {
        return networkFullId >> 1;
    }

    public static boolean hasData(int id) {
        return (id & 0x1) != 0;
    }

    @Deprecated
    @ToString
    @RequiredArgsConstructor
    static class Entry {
        String name;
        int id;
        Integer oldId;
        Integer oldData;
    }
}
