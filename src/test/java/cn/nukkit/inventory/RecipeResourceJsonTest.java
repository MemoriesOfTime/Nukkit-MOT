package cn.nukkit.inventory;

import cn.nukkit.utils.Config;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RecipeResourceJsonTest {

    @Test
    void bundledResourcesKeepEveryValueNumberTypeAndIterationOrder() throws Exception {
        for (String resource : List.of("recipes.json", "smithing.json", "recipes/furnace_xp.json")) {
            try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(stream, resource);
                String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                Config previous = new Config(Config.YAML).loadFromStream(
                        new java.io.ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
                Config replacement = CraftingManager.readRecipeJson(new StringReader(json));
                assertSameData(previous.getRootSection(), replacement.getRootSection(), resource);
            }
        }
    }

    @Test
    void integerBoundariesDecimalsNullsAndNestedDataKeepYamlTypes() {
        String json = """
                {"z":-2147483649,"a":2147483648,"max":9223372036854775807,
                 "big":9223372036854775808,"min":-9223372036854775808,
                 "decimal":0.7,"exponent":1e2,"negativeZero":-0.0,
                 "nested":[{"n":42,"b":true,"s":"001","nil":null}],"empty":[]}
                """;
        Config previous = new Config(Config.YAML).loadFromStream(
                new java.io.ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        Config replacement = CraftingManager.readRecipeJson(new StringReader(json));
        assertSameData(previous.getRootSection(), replacement.getRootSection(), "fixture");
    }

    private static void assertSameData(Object previous, Object replacement, String path) {
        if (previous instanceof Map<?, ?> before && replacement instanceof Map<?, ?> after) {
            assertEquals(new ArrayList<>(before.keySet()), new ArrayList<>(after.keySet()), path + " key order");
            for (Object key : before.keySet()) {
                assertSameData(before.get(key), after.get(key), path + "." + key);
            }
        } else if (previous instanceof List<?> before && replacement instanceof List<?> after) {
            assertEquals(before.size(), after.size(), path);
            for (int i = 0; i < before.size(); i++) {
                assertSameData(before.get(i), after.get(i), path + "[" + i + "]");
            }
        } else {
            assertEquals(previous, replacement, path);
            if (previous != null) {
                assertEquals(previous.getClass(), replacement.getClass(), path + " value type");
            }
        }
    }
}
