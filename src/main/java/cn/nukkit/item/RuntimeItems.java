package cn.nukkit.item;

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

@Log4j2
@UtilityClass
public class RuntimeItems {

    private static final Map<String, Integer> legacyString2LegacyInt = new HashMap<>();

    private static RuntimeItemMapping mapping361;
    private static RuntimeItemMapping mapping419;
    private static RuntimeItemMapping mapping440;
    private static RuntimeItemMapping mapping448;
    private static RuntimeItemMapping mapping475;
    private static RuntimeItemMapping mapping486;
    private static RuntimeItemMapping mapping503;
    private static RuntimeItemMapping mapping527;
    private static RuntimeItemMapping mapping534;
    private static RuntimeItemMapping mapping560;
    private static RuntimeItemMapping mapping567;
    private static RuntimeItemMapping mapping575;
    private static RuntimeItemMapping mapping582;
    private static RuntimeItemMapping mapping589;
    private static RuntimeItemMapping mapping594;
    private static RuntimeItemMapping mapping618;
    private static RuntimeItemMapping mapping630;
    private static RuntimeItemMapping mapping649;
    private static RuntimeItemMapping mapping662;

    private static boolean initialized;

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

        Map<String, MappingEntry> mappingEntries = new HashMap<>();
        Map<String, MappingEntry> mappingEntries630 = new HashMap<>();
        for (String legacyName : itemMapping.keySet()) {
            JsonObject convertData = itemMapping.getAsJsonObject(legacyName);
            for (String damageStr : convertData.keySet()) {
                String identifier = convertData.get(damageStr).getAsString();
                int damage = Integer.parseInt(damageStr);
                MappingEntry value = new MappingEntry(legacyName, damage);
                mappingEntries630.put(identifier, value);
                if ("minecraft:stone".equals(legacyName)) {
                    continue; // Stone 在1.20.50及以后拆分，对于以前的版本，不需要重复注册
                }
                mappingEntries.put(identifier, value);
            }
        }

        mapping361 = new RuntimeItemMapping(mappingEntries, ProtocolInfo.v1_12_0);
        mapping419 = new RuntimeItemMapping(mappingEntries, ProtocolInfo.v1_16_100);
        mapping440 = new RuntimeItemMapping(mappingEntries, ProtocolInfo.v1_17_0);
        mapping448 = new RuntimeItemMapping(mappingEntries, ProtocolInfo.v1_17_10);
        mapping475 = new RuntimeItemMapping(mappingEntries, ProtocolInfo.v1_18_0);
        mapping486 = new RuntimeItemMapping(mappingEntries, ProtocolInfo.v1_18_10);
        mapping503 = new RuntimeItemMapping(mappingEntries, ProtocolInfo.v1_18_30);
        mapping527 = new RuntimeItemMapping(mappingEntries, ProtocolInfo.v1_19_0);
        mapping534 = new RuntimeItemMapping(mappingEntries, ProtocolInfo.v1_19_10);
        mapping560 = new RuntimeItemMapping(mappingEntries, ProtocolInfo.v1_19_50);
        mapping567 = new RuntimeItemMapping(mappingEntries, ProtocolInfo.v1_19_60);
        mapping575 = new RuntimeItemMapping(mappingEntries, ProtocolInfo.v1_19_70);
        mapping582 = new RuntimeItemMapping(mappingEntries, ProtocolInfo.v1_19_80);
        mapping589 = new RuntimeItemMapping(mappingEntries, ProtocolInfo.v1_20_0);
        mapping594 = new RuntimeItemMapping(mappingEntries, ProtocolInfo.v1_20_10);
        mapping618 = new RuntimeItemMapping(mappingEntries, ProtocolInfo.v1_20_30);
        mapping630 = new RuntimeItemMapping(mappingEntries630, ProtocolInfo.v1_20_50);
        mapping649 = new RuntimeItemMapping(mappingEntries630, ProtocolInfo.v1_20_60);
        mapping662 = new RuntimeItemMapping(mappingEntries630, ProtocolInfo.v1_20_70);
    }

    public static RuntimeItemMapping getMapping(int protocolId) {
        if (protocolId >= ProtocolInfo.v1_20_70) {
            return mapping662;
        } else if (protocolId >= ProtocolInfo.v1_20_60) {
            return mapping649;
        } else if (protocolId >= ProtocolInfo.v1_20_50) {
            return mapping630;
        } else if (protocolId >= ProtocolInfo.v1_20_30_24) {
            return mapping618;
        } else if (protocolId >= ProtocolInfo.v1_20_10_21) {
            return mapping594;
        } else if (protocolId >= ProtocolInfo.v1_20_0_23) {
            return mapping589;
        } else if (protocolId >= ProtocolInfo.v1_19_80) {
            return mapping582;
        } else if (protocolId >= ProtocolInfo.v1_19_70_24) {
            return mapping575;
        } else if (protocolId >= ProtocolInfo.v1_19_60) {
            return mapping567;
        } else if (protocolId >= ProtocolInfo.v1_19_50_20) {
            return mapping560;
        } else if (protocolId >= ProtocolInfo.v1_19_10) {
            return mapping534;
        } else if (protocolId >= ProtocolInfo.v1_19_0_29) {
            return mapping527;
        } else if (protocolId >= ProtocolInfo.v1_18_30) {
            return mapping503;
        } else if (protocolId >= ProtocolInfo.v1_18_10_26) {
            return mapping486;
        } else if (protocolId >= ProtocolInfo.v1_18_0) {
            return mapping475;
        } else if (protocolId >= ProtocolInfo.v1_17_10) {
            return mapping448;
        } else if (protocolId >= ProtocolInfo.v1_17_0) {
            return mapping440;
        } else if (protocolId >= ProtocolInfo.v1_16_100) {
            return mapping419;
        }
        return mapping361;
    }

    public static int getLegacyIdFromLegacyString(String identifier) {
        return legacyString2LegacyInt.getOrDefault(identifier, -1);
    }

    @Data
    public static class MappingEntry {
        private final String legacyName;
        private final int damage;
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

    @ToString
    @RequiredArgsConstructor
    static class Entry {
        String name;
        int id;
        Integer oldId;
        Integer oldData;
    }
}
