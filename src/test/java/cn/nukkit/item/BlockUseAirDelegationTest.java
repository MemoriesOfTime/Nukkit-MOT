package cn.nukkit.item;

import cn.nukkit.MockServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Air-use items must not be activatable from block item-use: Bedrock clients tap blocks to
 * interact, and a real use arrives as a separate air-use transaction.
 */
class BlockUseAirDelegationTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @Test
    void airUseItemsAreNotBlockActivatable() {
        for (Item item : List.of(
                Item.get(ItemID.FISHING_ROD),
                Item.get(ItemID.IRON_HELMET),
                Item.get(ItemID.EMPTY_MAP))) {
            assertFalse(item.canBeActivated(), item.getClass().getSimpleName());
        }
    }
}
