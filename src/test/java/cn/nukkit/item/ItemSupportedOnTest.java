package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.item.RuntimeItemMapping.LegacyEntry;
import cn.nukkit.potion.Potion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 Item.isSupportedOn 在所有运行时版本上的一致性。
 * <p>
 * 该测试会自动从 RuntimeItems.VALUES 发现待测版本，
 * 并从 NAMESPACED_ID_ITEM 与各版本 RuntimeItemMapping 中发现待测物品，
 * 以便在新增协议版本或新增核心物品后，无需手动维护测试用例。
 */
public class ItemSupportedOnTest {

    private static Field gameVersionField;
    private static List<MappingContext> mappingContexts;

    @BeforeAll
    static void setup() throws Exception {
        MockServer.init();
        gameVersionField = RuntimeItemMapping.class.getDeclaredField("gameVersion");
        gameVersionField.setAccessible(true);
        mappingContexts = collectMappingContexts();
    }

    /**
     * 验证所有在各版本 RuntimeItemMapping 中注册的物品都能被测试覆盖到，
     * 且在自己的注册版本上调用 isSupportedOn 不会抛异常。
     * <p>
     * 如果该物品在自己的注册版本上返回 supported，则必须能被该版本实际编码。
     * <p>
     * 该测试同时覆盖两条创建链路：
     * 1. Item.fromString(identifier) 用于覆盖 StringItem 及 namespaced 物品
     * 2. Item.get(legacyId, damage) 用于覆盖 legacy/damage 变体
     */
    @Test
    public void testRuntimeMappedItemsSupportTheirOwnVersion() {
        int testedItems = 0;

        for (MappingContext context : mappingContexts) {
            RuntimeItemMapping mapping = context.mapping();
            GameVersion gameVersion = context.gameVersion();

            for (String identifier : sortedIdentifiers(mapping.getName2RuntimeId().keySet())) {
                Item namespacedItem = Item.fromString(identifier);
                if (isConstructibleItem(identifier, namespacedItem)) {
                    assertEncodableWhenSupported(new ItemCandidate(namespacedItem, "identifier " + identifier),
                            context);
                    testedItems++;
                }

                LegacyEntry legacyEntry = mapping.fromIdentifier(identifier);
                if (legacyEntry == null) {
                    continue;
                }

                Item legacyItem = Item.get(legacyEntry.getLegacyId(), legacyEntry.getDamage());
                if (isConstructibleLegacyItem(legacyEntry, legacyItem)) {
                    assertEncodableWhenSupported(new ItemCandidate(
                            legacyItem,
                            "legacy " + legacyEntry.getLegacyId() + ":" + legacyEntry.getDamage() + " <- " + identifier),
                            context);
                    testedItems++;
                }
            }
        }

        assertTrue(testedItems > 0,
                "Should have verified at least one runtime-mapped item");
    }

    /**
     * 验证所有自动发现到的物品，只要 isSupportedOn(version) 返回 true，
     * 就必须能被该版本 RuntimeItemMapping 实际编码。
     * <p>
     * 这会自动抓出“忘记为新物品收紧旧版本 isSupportedOn”的问题，
     * 也能在新增版本后自动把该版本纳入校验。
     */
    @Test
    public void testSupportedItemsAreEncodableOnEveryRuntimeVersion() {
        Map<String, ItemCandidate> candidates = collectItemCandidates();
        assertFalse(candidates.isEmpty(), "Should have discovered item candidates automatically");

        for (ItemCandidate candidate : candidates.values()) {
            for (MappingContext context : mappingContexts) {
                assertEncodableWhenSupported(candidate, context);
            }
        }
    }

    /**
     * 验证 AIR 物品在所有版本上都返回 true。
     * isSupportedOnMapping 中对 AIR 有特殊处理，始终返回 true。
     */
    @Test
    public void testAirAlwaysSupported() {
        Item air = Item.get(Item.AIR);
        assertNotNull(air);
        for (MappingContext context : mappingContexts) {
            assertTrue(air.isSupportedOn(context.gameVersion()),
                    "AIR should be supported on " + context.gameVersion());
        }
    }

    @Test
    public void testPotionMetaSupportBoundaries() {
        assertEquals(42, Potion.SLOWNESS_IV);
        assertEquals(43, Potion.WIND_CHARGED);
        assertEquals(44, Potion.WEAVING);
        assertEquals(45, Potion.OOZING);
        assertEquals(46, Potion.INFESTED);

        assertTrue(new ItemPotion(Potion.SLOWNESS_IV).isSupportedOn(GameVersion.V1_20_80));
        assertTrue(new ItemPotionSplash(Potion.SLOWNESS_IV).isSupportedOn(GameVersion.V1_20_80));
        assertTrue(new ItemPotionLingering(Potion.SLOWNESS_IV).isSupportedOn(GameVersion.V1_20_80));

        assertFalse(new ItemPotion(Potion.WIND_CHARGED).isSupportedOn(GameVersion.V1_20_80));
        assertFalse(new ItemPotionSplash(Potion.WEAVING).isSupportedOn(GameVersion.V1_20_80));
        assertFalse(new ItemPotionLingering(Potion.INFESTED).isSupportedOn(GameVersion.V1_20_80));

        assertTrue(new ItemPotion(Potion.WIND_CHARGED).isSupportedOn(GameVersion.V1_21_0));
        assertTrue(new ItemPotionSplash(Potion.WEAVING).isSupportedOn(GameVersion.V1_21_0));
        assertTrue(new ItemPotionLingering(Potion.INFESTED).isSupportedOn(GameVersion.V1_21_0));

        assertFalse(new ItemPotion(Potion.INFESTED + 1).isSupportedOn(GameVersion.V1_21_0));
    }

    /**
     * 验证最新版本中所有可通过 NAMESPACED_ID_ITEM 自动发现的核心物品，
     * 在最新版本上必须返回 supported 且能够被编码。
     * <p>
     * 新增核心物品只要注册到 NAMESPACED_ID_ITEM，就会被此测试自动纳入。
     */
    @Test
    public void testLatestVersionItemsSupported() {
        GameVersion latestVersion = GameVersion.getLastVersion();
        RuntimeItemMapping latestMapping = RuntimeItems.getMapping(latestVersion);
        List<String> errors = new ArrayList<>();
        int verifiedCount = 0;

        for (String identifier : new TreeSet<>(Item.NAMESPACED_ID_ITEM.keySet())) {
            Item item = Item.fromString(identifier);
            if (!isConstructibleItem(identifier, item)) {
                continue;
            }

            if (!item.isSupportedOn(latestVersion)) {
                errors.add(identifier + " should be supported on latest version " + latestVersion);
            } else {
                assertCanEncode(latestMapping, latestVersion, item,
                        "latest namespaced item " + identifier);
            }

            verifiedCount++;
        }

        assertTrue(errors.isEmpty(),
                "Items from NAMESPACED_ID_ITEM should be supported on latest version:\n"
                        + String.join("\n", errors));
        assertTrue(verifiedCount > 0, "Should have verified at least some items");
    }

    private static List<MappingContext> collectMappingContexts() throws IllegalAccessException {
        List<MappingContext> contexts = new ArrayList<>(RuntimeItems.VALUES.length);
        for (RuntimeItemMapping mapping : RuntimeItems.VALUES) {
            contexts.add(new MappingContext(mapping, (GameVersion) gameVersionField.get(mapping)));
        }
        contexts.sort(Comparator.comparingInt(context -> context.gameVersion().getProtocol()));
        return contexts;
    }

    private static Map<String, ItemCandidate> collectItemCandidates() {
        Map<String, ItemCandidate> candidates = new LinkedHashMap<>();

        for (String identifier : new TreeSet<>(Item.NAMESPACED_ID_ITEM.keySet())) {
            addCandidate(candidates, "namespaced:" + identifier, Item.fromString(identifier), identifier);
        }

        for (MappingContext context : mappingContexts) {
            RuntimeItemMapping mapping = context.mapping();
            GameVersion gameVersion = context.gameVersion();

            for (String identifier : sortedIdentifiers(mapping.getName2RuntimeId().keySet())) {
                addCandidate(candidates,
                        "runtime-name:" + identifier,
                        Item.fromString(identifier),
                        identifier + "@" + gameVersion);

                LegacyEntry legacyEntry = mapping.fromIdentifier(identifier);
                if (legacyEntry != null) {
                    Item legacyItem = Item.get(legacyEntry.getLegacyId(), legacyEntry.getDamage());
                    addCandidate(candidates,
                            "legacy:" + legacyEntry.getLegacyId() + ":" + legacyEntry.getDamage(),
                            legacyItem,
                            identifier + "@" + gameVersion);
                }
            }
        }

        return candidates;
    }

    private static void addCandidate(Map<String, ItemCandidate> candidates, String uniqueKey, Item item, String source) {
        if (item == null || item.getId() == Item.AIR) {
            return;
        }
        candidates.putIfAbsent(uniqueKey, new ItemCandidate(item, source));
    }

    private static void assertEncodableWhenSupported(ItemCandidate candidate, MappingContext context) {
        Item item = candidate.item();
        GameVersion gameVersion = context.gameVersion();
        boolean supported = assertDoesNotThrow(() -> item.isSupportedOn(gameVersion),
                candidate.source() + " isSupportedOn should not throw on " + gameVersion);

        if (supported) {
            assertCanEncode(context.mapping(), gameVersion, item, candidate.source());
        }
    }

    private static void assertCanEncode(RuntimeItemMapping mapping, GameVersion gameVersion, Item item, String source) {
        if (item.getId() == Item.AIR) {
            return;
        }

        if (item instanceof StringItem) {
            assertTrue(mapping.getNetworkIdByNamespaceId(item.getNamespaceId()).isPresent(),
                    source + " claims support on " + gameVersion
                            + " but mapping does not contain namespace id " + item.getNamespaceId());
            return;
        }

        assertDoesNotThrow(() -> mapping.getNetworkId(item),
                source + " claims support on " + gameVersion + " but mapping can not encode item " + item);
    }

    private static boolean isConstructibleItem(String identifier, Item item) {
        return item.getId() != Item.AIR || "minecraft:air".equals(identifier);
    }

    private static boolean isConstructibleLegacyItem(LegacyEntry legacyEntry, Item item) {
        return item.getId() != Item.AIR || legacyEntry.getLegacyId() == Item.AIR;
    }

    private static Collection<String> sortedIdentifiers(Collection<String> identifiers) {
        return new TreeSet<>(identifiers);
    }

    private record MappingContext(RuntimeItemMapping mapping, GameVersion gameVersion) {
    }

    private record ItemCandidate(Item item, String source) {
    }
}
