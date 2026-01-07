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

    private static RuntimeItemMapping mapping;

    public static RuntimeItemMapping[] VALUES;

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

        mapping = new RuntimeItemMapping(mappingEntries, ProtocolInfo.v1_16_100);
        VALUES = new RuntimeItemMapping[]{mapping};
    }

    public static RuntimeItemMapping getMapping(int protocolId) {
        return mapping;
    }

    public static int getLegacyIdFromLegacyString(String identifier) {
        return legacyString2LegacyInt.getOrDefault(identifier, -1);
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
