package cn.nukkit.item;

import cn.nukkit.MockServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** Legacy ItemID names retain meaning when the explicit vanilla namespace is used. */
class ItemLegacyNamespaceTest {
    @BeforeAll
    static void initialize() {
        MockServer.init();
    }

    @ParameterizedTest
    @CsvSource({"totem,450", "appleenchanted,466", "clownfish,461", "cooked_fish,350"})
    void explicitVanillaNamespaceKeepsExistingLegacyId(String name, int id) {
        Item bare = Item.fromString(name);
        Item qualified = Item.fromString("minecraft:" + name);
        assertEquals(id, bare.getId(), "existing bare alias must remain available");
        assertEquals(id, qualified.getId(), "namespace must not turn the existing alias into air");
        assertEquals(bare.getDamage(), qualified.getDamage());
        qualified.setCount(12);
        assertEquals(1, Item.fromString("minecraft:" + name).getCount(), "lookup returns independent items");
    }

    @ParameterizedTest
    @CsvSource({"totem,450", "appleenchanted,466", "clownfish,461", "cooked_fish,350"})
    void explicitVanillaNamespacePreservesMetadataAndNormalization(String name, int id) {
        Item item = Item.fromString("  MINECRAFT:" + name.toUpperCase(java.util.Locale.ROOT) + ":2  ");
        assertEquals(id, item.getId());
        assertEquals(2, item.getDamage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"totem", "appleenchanted", "clownfish", "cooked_fish"})
    void unknownNamespaceCannotBorrowVanillaReflection(String name) {
        assertEquals(Item.AIR, Item.fromString("unknown_namespace:" + name).getId());
        assertEquals(Item.AIR, Item.fromString("unknown_namespace:" + name + ":2").getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"minecraft:fish", "minecraft:speckled_melon", "minecraft:chorus_fruit_popped",
            "minecraft:missing_legacy_item", "missing_legacy_item", "minecraft:minecraft:totem"})
    void unsupportedNamesKeepExistingRefusal(String name) {
        assertEquals(Item.AIR, Item.fromString(name).getId());
    }

    @ParameterizedTest
    @CsvSource({"minecraft:stone,1,0", "minecraft:totem_of_undying,450,0", "450:2,450,2"})
    void canonicalAndNumericLookupKeepsExistingMeaning(String name, int id, int damage) {
        Item item = Item.fromString(name);
        assertEquals(id, item.getId());
        assertEquals(damage, item.getDamage());
    }
}
