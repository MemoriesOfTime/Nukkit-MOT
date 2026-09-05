package cn.nukkit.item;

import cn.nukkit.MockServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Item.get 对无注册类数字 id 的回退归一行为。
 * <p>
 * Fallback normalization in Item.get for numeric ids without a registered class.
 */
class ItemGetFallbackTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @BeforeEach
    void resetServer() {
        MockServer.reset();
    }

    @Test
    void classlessLegacyIdNormalizesToTypedStringItem() {
        // 519 = minecraft:copper_ingot，无数字 id 注册类，旧实现返回裸 Item 造成双表示
        Item item = Item.get(519, 0, 3);

        assertTrue(item instanceof ItemCopperIngot);
        assertEquals("minecraft:copper_ingot", ((StringItem) item).getNamespaceId());
        assertEquals(3, item.getCount());
        assertEquals(ItemID.STRING_IDENTIFIED_ITEM, item.getId());
    }

    @Test
    void unknownIdFallsBackToBareItemWithSameId() {
        // 超出 list 数组（65535）的 id 走异常回退，逆查表无此键 → 裸 Item
        Item item = Item.get(70000, 0, 1);

        assertEquals(Item.class, item.getClass());
        assertEquals(70000, item.getId());
        assertEquals(1, item.getCount());
    }

    @Test
    void reverseLookupResolvesIdentifierForLegacyId() {
        assertEquals("minecraft:copper_ingot", RuntimeItems.getLegacyStringFromLegacyId(519));
        assertNull(RuntimeItems.getLegacyStringFromLegacyId(70000));
    }
}
