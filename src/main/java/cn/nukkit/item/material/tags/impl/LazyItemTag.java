package cn.nukkit.item.material.tags.impl;

import cn.nukkit.item.material.ItemType;
import cn.nukkit.item.material.ItemTypes;
import cn.nukkit.item.material.tags.ItemTag;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStreamReader;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

@Slf4j
@EqualsAndHashCode
@ToString
public class LazyItemTag implements ItemTag {

    private static final Map<String, Set<String>> VANILLA_DEFINITIONS;

    private final String tag;
    private Set<ItemType> itemTypes;

    public LazyItemTag(String tag) {
        this.tag = tag;
    }

    private Set<ItemType> load() {
        Set<String> definitions = VANILLA_DEFINITIONS.get(tag);
        if (definitions == null) {
            throw new IllegalStateException("Unknown vanilla tag: " + this.tag);
        }

        Set<ItemType> itemTypes = new ObjectOpenHashSet<>();
        for (String definition : definitions) {
            ItemType material = ItemTypes.get(definition);
            if (material != null) {
                itemTypes.add(material);
            }
        }

        return Collections.unmodifiableSet(itemTypes);
    }

    public Set<ItemType> getItemTypes() {
        if (this.itemTypes == null) {
            this.itemTypes = this.load();
        }
        return this.itemTypes;
    }

    static {
        Map<String, Set<String>> parsed = Collections.emptyMap();
        var stream = LazyItemTag.class.getClassLoader().getResourceAsStream("item_tags.json");
        if (stream != null) {
            try (var reader = new InputStreamReader(stream)) {
                var type = new TypeToken<Map<String, Set<String>>>() {
                }.getType();
                parsed = new Gson().fromJson(reader, type);
            } catch (Exception e) {
                log.error("Failed to load vanilla item tags", e);
            }
        }
        VANILLA_DEFINITIONS = parsed;
    }
}
