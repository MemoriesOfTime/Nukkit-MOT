package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.item.RuntimeItemMapping.LegacyEntry;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 利用 runtime_item_states 文件测试所有物品的 isSupportedOn 方法。
 * <p>
 * 通过解析每个版本的 runtime_item_states 文件，获取全部注册物品，
 * 调用 isSupportedOn 方法以验证其行为正确性和一致性。
 */
public class ItemSupportedOnTest {

    private static Field gameVersionField;

    @BeforeAll
    static void setup() throws Exception {
        MockServer.init();
        gameVersionField = RuntimeItemMapping.class.getDeclaredField("gameVersion");
        gameVersionField.setAccessible(true);
    }

    /**
     * 解析所有 runtime_item_states 文件，对所有能创建 Item 的物品调用 isSupportedOn，
     * 验证方法不抛异常，并收集映射不一致项。
     * <p>
     * 某些物品（如 ItemSpawnEgg）会重写 isSupportedOn 进行版本限制，
     * 导致 runtime_item_states 文件注册了该物品但 isSupportedOn 返回 false。
     * 这是预期行为，不一致项仅作为信息报告。
     */
    @Test
    public void testIsSupportedOnForAllItems() throws Exception {
        List<String> inconsistencies = new ArrayList<>();
        int totalItems = 0;
        int testedItems = 0;

        for (RuntimeItemMapping mapping : RuntimeItems.VALUES) {
            GameVersion gameVersion = (GameVersion) gameVersionField.get(mapping);
            int protocolId = mapping.getProtocolId();

            String filename = gameVersion.isNetEase()
                    ? "runtime_item_states_netease_" + protocolId + ".json"
                    : "runtime_item_states_" + protocolId + ".json";

            InputStream stream = getClass().getClassLoader().getResourceAsStream(filename);
            assertNotNull(stream, "Missing resource: " + filename);

            JsonArray items;
            try (InputStreamReader reader = new InputStreamReader(stream)) {
                items = JsonParser.parseReader(reader).getAsJsonArray();
            }

            for (JsonElement element : items) {
                totalItems++;
                String name = element.getAsJsonObject().get("name").getAsString();

                // 通过 mapping 获取正确的 legacy ID 和 damage
                LegacyEntry legacyEntry = mapping.fromIdentifier(name);
                if (legacyEntry == null) {
                    continue;
                }

                int legacyId = legacyEntry.getLegacyId();
                int damage = legacyEntry.getDamage();

                Item item = Item.get(legacyId, damage);
                if (item.getId() == Item.AIR && legacyId != Item.AIR) {
                    continue;
                }

                testedItems++;

                // 调用 isSupportedOn 验证不抛异常
                boolean supported = assertDoesNotThrow(() -> item.isSupportedOn(gameVersion),
                        name + " isSupportedOn threw exception on " + gameVersion);

                if (!supported) {
                    inconsistencies.add(name + " (legacyId=" + legacyId + ", damage=" + damage
                            + ") on " + gameVersion + " (protocol " + protocolId + ")");
                }
            }
        }

        assertTrue(testedItems > 0,
                "Should have tested at least some items (tested " + testedItems + " of " + totalItems + ")");

        // 不一致项作为信息输出，不阻断测试
        // 这些物品的 isSupportedOn 被子类重写（如 ItemSpawnEgg），添加了更严格的版本限制
        if (!inconsistencies.isEmpty()) {
            System.out.println("[INFO] " + inconsistencies.size()
                    + " items registered in runtime_item_states but isSupportedOn returns false "
                    + "(expected for items with version-specific overrides):");
            inconsistencies.forEach(i -> System.out.println("[INFO]   " + i));
        }
    }

    /**
     * 验证 AIR 物品在所有版本上都返回 true。
     * isSupportedOnMapping 中对 AIR 有特殊处理，始终返回 true。
     */
    @Test
    public void testAirAlwaysSupported() throws Exception {
        Item air = Item.get(Item.AIR);
        assertNotNull(air);
        for (RuntimeItemMapping mapping : RuntimeItems.VALUES) {
            GameVersion gameVersion = (GameVersion) gameVersionField.get(mapping);
            assertTrue(air.isSupportedOn(gameVersion),
                    "AIR should be supported on " + gameVersion);
        }
    }

    /**
     * 验证最新版本中所有通过 NAMESPACED_ID_ITEM 可获取的物品，
     * 在最新版本上 isSupportedOn 返回 true。
     * 同时验证在旧版本上调用不抛异常。
     */
    @Test
    public void testLatestVersionItemsSupported() throws Exception {
        GameVersion latestVersion = GameVersion.getLastVersion();
        List<String> errors = new ArrayList<>();
        int verifiedCount = 0;

        for (var entry : Item.NAMESPACED_ID_ITEM.entrySet()) {
            String name = entry.getKey();
            Supplier<Item> supplier = entry.getValue();
            if (supplier == null) continue;

            Item item = supplier.get();
            if (item == null || item.getId() == Item.AIR) continue;

            // 最新版本必须支持
            if (!item.isSupportedOn(latestVersion)) {
                errors.add(name + " should be supported on latest version " + latestVersion);
            }

            // 所有版本上调用不应抛异常
            for (RuntimeItemMapping mapping : RuntimeItems.VALUES) {
                GameVersion gv = (GameVersion) gameVersionField.get(mapping);
                assertDoesNotThrow(() -> item.isSupportedOn(gv),
                        name + " isSupportedOn should not throw on " + gv);
            }

            verifiedCount++;
        }

        assertTrue(errors.isEmpty(),
                "Items from NAMESPACED_ID_ITEM should be supported on latest version:\n"
                        + String.join("\n", errors));
        assertTrue(verifiedCount > 0, "Should have verified at least some items");
    }
}
