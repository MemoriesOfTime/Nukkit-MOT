package cn.nukkit.inventory;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.item.RuntimeItemMapping;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 recipes.json 调色板映射的选择逻辑。
 * <p>
 * 选择应以 dump 工具写入的 version（协议号）字段为准，并通过字符串 id 锚点验证。
 */
public class RecipeMappingAdaptiveTest {

    @BeforeAll
    static void setup() {
        MockServer.init();
    }

    @Test
    public void testVersionFieldMappingSelected() throws Exception {
        cn.nukkit.utils.Config config = loadRecipes();
        Map<String, Object> root = config.getRootSection();
        assertTrue(root.get("version") instanceof Number, "recipes.json should carry a version field");

        int version = ((Number) root.get("version")).intValue();
        RuntimeItemMapping selected = selectMapping();

        assertEquals(GameVersion.byProtocol(version, false).getProtocol(), selected.getProtocolId(),
                "selected mapping should match the version field of recipes.json");
    }

    @Test
    public void testSelectedMappingPassesAnchorValidation() throws Exception {
        List<Integer> anchorIds = new ArrayList<>();
        List<String> anchorNames = new ArrayList<>();
        collectAnchors(loadRecipes().getRootSection().get("recipes"), anchorIds, anchorNames);
        assertFalse(anchorIds.isEmpty(), "recipes.json should contain anchored numeric ids");

        RuntimeItemMapping mapping = selectMapping();
        assertNotNull(mapping, "adaptive selection should return a mapping");

        int matched = 0;
        for (int i = 0; i < anchorIds.size(); i++) {
            if (anchorNames.get(i).equals(mapping.getNamespacedIdByNetworkId(anchorIds.get(i)))) {
                matched++;
            }
        }
        assertEquals(anchorIds.size(), matched,
                "selected mapping " + mapping.getProtocolId() + " must resolve every anchor");
    }

    private static cn.nukkit.utils.Config loadRecipes() {
        cn.nukkit.utils.Config config = new cn.nukkit.utils.Config(cn.nukkit.utils.Config.YAML);
        return config.loadFromStream(
                RecipeMappingAdaptiveTest.class.getClassLoader().getResourceAsStream("recipes.json"));
    }

    private static RuntimeItemMapping selectMapping() throws Exception {
        Method select = CraftingManager.class.getDeclaredMethod("selectRecipeItemMapping", Map.class);
        select.setAccessible(true);
        return (RuntimeItemMapping) select.invoke(null, loadRecipes().getRootSection());
    }

    private static void collectAnchors(Object value, List<Integer> anchorIds, List<String> anchorNames) {
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            Number runtimeId = map.get("itemId") instanceof Number itemId ? itemId
                    : map.get("legacyId") instanceof Number legacyId ? legacyId : null;
            if (runtimeId != null && runtimeId.intValue() >= 0 && map.get("id") instanceof String identifier) {
                anchorIds.add(runtimeId.intValue());
                anchorNames.add(identifier);
            }
            for (Object element : map.values()) {
                collectAnchors(element, anchorIds, anchorNames);
            }
        } else if (value instanceof List) {
            for (Object element : (List<?>) value) {
                collectAnchors(element, anchorIds, anchorNames);
            }
        }
    }
}
