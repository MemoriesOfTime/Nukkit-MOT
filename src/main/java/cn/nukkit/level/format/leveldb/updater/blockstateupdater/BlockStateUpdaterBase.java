package cn.nukkit.level.format.leveldb.updater.blockstateupdater;

import cn.nukkit.level.format.leveldb.updater.blockstateupdater.util.tagupdater.CompoundTagUpdaterContext;
import com.google.gson.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BlockStateUpdaterBase implements BlockStateUpdater {
    public static final BlockStateUpdater INSTANCE = new BlockStateUpdaterBase();

    public static final Map<String, Map<String, Object>[]> LEGACY_BLOCK_DATA_MAP = new HashMap<>();
    private static final Gson JSON_MAPPER = new Gson();

    static {
        JsonObject node;
        try (InputStream stream = BlockStateUpdater.class.getClassLoader().getResourceAsStream("legacy_block_data_map.json")) {
            assert stream != null;
            node = (JsonObject) JSON_MAPPER.toJsonTree(JSON_MAPPER.fromJson(new InputStreamReader(stream), Map.class));
        } catch (IOException e) {
            throw new AssertionError("Error loading legacy block data map", e);
        }

        for (Map.Entry<String, JsonElement> entry : node.entrySet()) {
            String name = entry.getKey();
            JsonArray stateNodes = entry.getValue().getAsJsonArray();

            int size = stateNodes.size();
            Map<String, Object>[] states = new Map[size];
            for (int i = 0; i < size; i++) {
                states[i] = convertStateToCompound(stateNodes.get(i).getAsJsonObject());
            }

            LEGACY_BLOCK_DATA_MAP.put(name, states);
        }
    }

    private static Map<String, Object> convertStateToCompound(JsonObject node) {
        Map<String, Object> tag = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : node.entrySet()) {
            String name = entry.getKey();
            JsonElement value = entry.getValue();
            if (value.isJsonPrimitive()) {
                JsonPrimitive primitive = entry.getValue().getAsJsonPrimitive();
                if (primitive.isBoolean()) {
                    tag.put(name, primitive.getAsBoolean());
                } else if (primitive.isNumber()) {
                    tag.put(name, primitive.getAsInt());
                } else if (primitive.isString()) {
                    tag.put(name, primitive.getAsString());
                } else {
                    throw new UnsupportedOperationException("Invalid state type");
                }
            } else throw new UnsupportedOperationException("Invalid state type");
        }
        return tag;
    }

    @Override
    public void registerUpdaters(CompoundTagUpdaterContext context) {
        context.addUpdater(0, 0, 0)
                .regex("name", "minecraft:.+")
                .regex("val", "[0-9]+")
                .addCompound("states")
                .tryEdit("states", helper -> {
                    Map<String, Object> tag = helper.getCompoundTag();
                    Map<String, Object> parent = helper.getParent();
                    String id = (String) parent.get("name");
                    short val = (short) parent.get("val");
                    Map<String, Object>[] statesArray = LEGACY_BLOCK_DATA_MAP.get(id);
                    if (statesArray != null) {
                        if (val >= statesArray.length) val = 0;
                        tag.putAll(statesArray[val]);
                    }
                })
                .remove("val");
    }
}
