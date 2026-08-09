package cn.nukkit.block.tags;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.block.BlockUnknown;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@EqualsAndHashCode
@ToString
public class LazyBlockTag implements BlockTag {

    public static final Map<String, Set<String>> VANILLA_DEFINITIONS;

    private static final Map<String, Integer> BLOCK_ID_BY_NAME;

    private final String tag;
    private IntSet blockId;

    public LazyBlockTag(String tag) {
        this.tag = tag;
    }

    private IntSet load() {
        Set<String> definitions = VANILLA_DEFINITIONS.get(tag);

        if (definitions == null) {
            throw new IllegalStateException("Unknown vanilla tag: " + tag);
        }

        IntSet blockIds = new IntOpenHashSet();

        for (String definition : definitions) {
            Integer id = BLOCK_ID_BY_NAME.get(definition);

            if (id == null) {
                log.debug("Unknown block '{}' in vanilla tag '{}'", definition, tag);
                continue;
            }

            blockIds.add(id);
        }

        return blockIds;
    }

    @Override
    public IntSet getBlockId() {
        if (blockId == null) {
            blockId = load();
        }

        return blockId;
    }

    private static Map<String, Integer> createBlockIdMap() {
        Map<String, Integer> map = new HashMap<>();

        Block.init();

        for (Field field : BlockID.class.getFields()) {
            if (field.getType() != int.class) {
                continue;
            }

            try {
                int id = field.getInt(null);
                Block block = Block.get(id);

                if (block instanceof BlockUnknown) {
                    continue;
                }

                String name = field.getName().toLowerCase(Locale.ROOT);
                map.put("minecraft:" + name, id);
            } catch (IllegalAccessException e) {
                log.error("Failed to read BlockID.{}", field.getName(), e);
            }
        }

        return Collections.unmodifiableMap(map);
    }

    static {
        BLOCK_ID_BY_NAME = createBlockIdMap();

        Map<String, Set<String>> parsed = Collections.emptyMap();

        var stream = LazyBlockTag.class.getClassLoader().getResourceAsStream("tags/block_tags.json");

        if (stream != null) {
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                Type type = new TypeToken<Map<String, Set<String>>>() {
                }.getType();
                parsed = new Gson().fromJson(reader, type);
            } catch (Exception e) {
                log.error("Failed to load vanilla block tags", e);
            }
        } else {
            log.warn("Could not find tags/block_tags.json");
        }

        VANILLA_DEFINITIONS = parsed;
    }
}
