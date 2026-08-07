package cn.nukkit.network.protocol.types.inventory;

import cn.nukkit.GameVersion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Regression test for the NetEase {@link ContainerSlotType} wire mapping.
 * 网易客户端在标准枚举索引 17 (RECIPE_ITEMS) 处插入了一个额外的 RECIPE_CUSTOM 枚举值，
 * 因此所有标准索引 >= 17 的槽位类型在线路上都比标准多 1。
 * <p>
 * NetEase inserts an extra RECIPE_CUSTOM enum value at standard index 17, shifting
 * every standard slot type at index >= 17 up by one on the wire. Without the offset
 * a NetEase client's player-inventory clicks are decoded as SHULKER_BOX and silently
 * rejected by the server-authoritative inventory flow.
 */
class ContainerSlotTypeNetEaseTest {

    private static final GameVersion NETEASE = GameVersion.V1_21_124_NETEASE;
    private static final GameVersion STANDARD = GameVersion.V1_21_124;

    /**
     * The exact scenario from the bug report: a NetEase client interacting with its
     * own inventory sends wire byte 30, which must decode as INVENTORY (not SHULKER_BOX).
     */
    @Test
    void netEaseInventoryByteIs30() {
        // encode: INVENTORY → wire 30
        assertEquals(30, ContainerSlotType.INVENTORY.getId(NETEASE));
        // decode: wire 30 → INVENTORY
        assertEquals(ContainerSlotType.INVENTORY, ContainerSlotType.fromId(30, NETEASE));
    }

    @Test
    void standardInventoryByteIs29() {
        assertEquals(29, ContainerSlotType.INVENTORY.getId(STANDARD));
        assertEquals(ContainerSlotType.INVENTORY, ContainerSlotType.fromId(29, STANDARD));
        // Standard byte 30 is still SHULKER_BOX.
        assertEquals(ContainerSlotType.SHULKER_BOX, ContainerSlotType.fromId(30, STANDARD));
    }

    @Test
    void netEaseOffsetIsSymmetricForAllShiftedTypes() {
        // Everything at standard index >= 17 (RECIPE_ITEMS) is shifted +1 on the NetEase wire.
        for (ContainerSlotType type : ContainerSlotType.values()) {
            if (type.getId() < ContainerSlotType.RECIPE_ITEMS.getId()) {
                continue; // unaffected
            }
            int standardByte = type.getId();
            int neteaseByte = type.getId(NETEASE);
            assertEquals(standardByte + 1, neteaseByte,
                    "NetEase wire byte should be standard+1 for " + type);
            assertEquals(type, ContainerSlotType.fromId(neteaseByte, NETEASE),
                    "NetEase decode should round-trip for " + type);
        }
    }

    @Test
    void netEaseTypesBelow17AreUnchanged() {
        assertEquals(ContainerSlotType.HOTBAR_AND_INVENTORY.getId(),
                ContainerSlotType.HOTBAR_AND_INVENTORY.getId(NETEASE));
        assertEquals(ContainerSlotType.ANVIL_INPUT.getId(),
                ContainerSlotType.ANVIL_INPUT.getId(NETEASE));
    }

    /**
     * NetEase wire byte 17 is the NetEase-only RECIPE_CUSTOM, which has no
     * equivalent in the standard enum, so it decodes to null.
     */
    @Test
    void netEaseRecipeCustomByte17IsUnknown() {
        assertNull(ContainerSlotType.fromId(17, NETEASE));
    }

    @Test
    void hotbarAndInventoryRoundTrip() {
        // HOTBAR is a very common source slot for item transfers.
        int byte29 = ContainerSlotType.HOTBAR.getId(NETEASE);
        assertEquals(ContainerSlotType.HOTBAR, ContainerSlotType.fromId(byte29, NETEASE));
    }

    /**
     * The NetEase RECIPE_CUSTOM offset applies to every NetEase protocol version,
     * not just the latest. Sanity-check the inventory mapping on all of them.
     */
    @Test
    void inventoryMapsToByte30OnAllNetEaseVersions() {
        GameVersion[] netEaseVersions = {
                GameVersion.V1_20_50_NETEASE,
                GameVersion.V1_21_2_NETEASE,
                GameVersion.V1_21_50_NETEASE,
                GameVersion.V1_21_93_NETEASE,
                GameVersion.V1_21_124_NETEASE,
        };
        for (GameVersion version : netEaseVersions) {
            assertEquals(30, ContainerSlotType.INVENTORY.getId(version),
                    "INVENTORY wire byte for " + version);
            assertEquals(ContainerSlotType.INVENTORY, ContainerSlotType.fromId(30, version),
                    "byte 30 decode for " + version);
        }
    }
}
