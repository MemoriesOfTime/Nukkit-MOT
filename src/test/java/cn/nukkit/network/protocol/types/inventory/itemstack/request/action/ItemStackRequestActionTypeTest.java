package cn.nukkit.network.protocol.types.inventory.itemstack.request.action;

import cn.nukkit.GameVersion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ItemStackRequestActionTypeTest {

    @Test
    void itemContainerActionIdsRemainDistinctBeforeV712() {
        assertSame(ItemStackRequestActionType.PLACE_IN_ITEM_CONTAINER, ItemStackRequestActionType.fromId(7, GameVersion.V1_18_10));
        assertSame(ItemStackRequestActionType.TAKE_FROM_ITEM_CONTAINER, ItemStackRequestActionType.fromId(8, GameVersion.V1_20_50));
        assertNull(ItemStackRequestActionType.fromId(7, GameVersion.V1_21_20));
        assertNull(ItemStackRequestActionType.fromId(8, GameVersion.V1_21_20));
    }
}
