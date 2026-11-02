package cn.nukkit.network.protocol.types.inventory;

import cn.nukkit.GameVersion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 配方书容器槽位类型 (64-66) 的版本边界回归。
 * <p>
 * International decoding recognizes recipe slots 64-66 from v944 (1.26.10).
 * The current upstream encoder rejects these slots in the v712-v898 window, but retains
 * older fallback IDs before v712. Characterize that API asymmetry without treating its
 * legacy encode result as proof that those clients can decode the slot. NetEase uses its
 * separate one-slot wire offset.
 */
class ContainerSlotTypeRecipeContainersTest {

    @Test
    void recipeContainersRoundTripFromV1_26_10() {
        assertEquals(ContainerSlotType.RECIPE_FOOD_CONTAINER,
                ContainerSlotType.fromId(ContainerSlotType.RECIPE_FOOD_CONTAINER.getId(), GameVersion.V1_26_10));
        assertEquals(ContainerSlotType.RECIPE_BLOCKS_CONTAINER,
                ContainerSlotType.fromId(ContainerSlotType.RECIPE_BLOCKS_CONTAINER.getId(), GameVersion.V1_26_10));
        assertEquals(ContainerSlotType.RECIPE_FURNACE_ITEMS_CONTAINER,
                ContainerSlotType.fromId(ContainerSlotType.RECIPE_FURNACE_ITEMS_CONTAINER.getId(), GameVersion.V1_26_10));
        assertEquals(64, ContainerSlotType.RECIPE_FOOD_CONTAINER.getId(GameVersion.V1_26_10));
        assertEquals(66, ContainerSlotType.RECIPE_FURNACE_ITEMS_CONTAINER.getId(GameVersion.V1_26_10));
    }

    @Test
    void recipeContainersDecodeAsNullBelowV1_26_10() {
        for (GameVersion version : new GameVersion[]{
                GameVersion.V1_19_80, GameVersion.V1_20_50, GameVersion.V1_21_20,
                GameVersion.V1_21_124, GameVersion.V1_26_0}) {
            for (int id = 64; id <= 66; id++) {
                String message = "wire id " + id + " must not decode on " + version;
                assertNull(ContainerSlotType.fromId(id, version), message);
            }
        }
    }

    @Test
    void recipeContainersRefuseToEncodeFromV1_21_20UntilV1_26_10() {
        for (GameVersion version : new GameVersion[]{
                GameVersion.V1_21_20, GameVersion.V1_21_124, GameVersion.V1_26_0}) {
            for (ContainerSlotType type : new ContainerSlotType[]{
                    ContainerSlotType.RECIPE_FOOD_CONTAINER,
                    ContainerSlotType.RECIPE_BLOCKS_CONTAINER,
                    ContainerSlotType.RECIPE_FURNACE_ITEMS_CONTAINER}) {
                String message = type + " must not encode on " + version;
                assertThrows(IllegalArgumentException.class, () -> type.getId(version), message);
            }
        }
    }

    @Test
    void legacyEncodingKeepsUpstreamIdsWithoutClaimingDecodeSupport() {
        for (GameVersion version : new GameVersion[]{GameVersion.V1_19_80, GameVersion.V1_20_50}) {
            assertEquals(64, ContainerSlotType.RECIPE_FOOD_CONTAINER.getId(version));
            assertEquals(65, ContainerSlotType.RECIPE_BLOCKS_CONTAINER.getId(version));
            assertEquals(66, ContainerSlotType.RECIPE_FURNACE_ITEMS_CONTAINER.getId(version));
            for (int id = 64; id <= 66; id++) assertNull(ContainerSlotType.fromId(id, version));
        }
    }

    @Test
    void netEaseRecipeContainersUseTheirMappedOffset() {
        for (GameVersion version : new GameVersion[]{GameVersion.V1_20_50_NETEASE, GameVersion.V1_21_124_NETEASE}) {
            assertEquals(65, ContainerSlotType.RECIPE_FOOD_CONTAINER.getId(version));
            assertEquals(66, ContainerSlotType.RECIPE_BLOCKS_CONTAINER.getId(version));
            assertEquals(67, ContainerSlotType.RECIPE_FURNACE_ITEMS_CONTAINER.getId(version));
            assertEquals(ContainerSlotType.RECIPE_FOOD_CONTAINER, ContainerSlotType.fromId(65, version));
            assertEquals(ContainerSlotType.RECIPE_BLOCKS_CONTAINER, ContainerSlotType.fromId(66, version));
            assertEquals(ContainerSlotType.RECIPE_FURNACE_ITEMS_CONTAINER, ContainerSlotType.fromId(67, version));
        }
    }

    @Test
    void newReservedContainersBeginAtV1_26_60() {
        for (ContainerSlotType type : new ContainerSlotType[]{ContainerSlotType.RESERVED_CONTAINER_A,
                ContainerSlotType.RESERVED_CONTAINER_B, ContainerSlotType.RESERVED_CONTAINER_C,
                ContainerSlotType.RESERVED_CONTAINER_D}) {
            assertThrows(IllegalArgumentException.class, () -> type.getId(GameVersion.V1_26_10));
            assertNull(ContainerSlotType.fromId(type.getId(), GameVersion.V1_26_10));
            assertEquals(type.getId(), type.getId(GameVersion.V1_26_60));
            assertEquals(type, ContainerSlotType.fromId(type.getId(), GameVersion.V1_26_60));
        }
    }

    @Test
    void dynamicContainerBoundaryIsUnchanged() {
        assertEquals(ContainerSlotType.DYNAMIC_CONTAINER,
                ContainerSlotType.fromId(63, GameVersion.V1_21_20));
        assertEquals(ContainerSlotType.DYNAMIC_CONTAINER,
                ContainerSlotType.fromId(63, GameVersion.V1_26_0));
        assertNull(ContainerSlotType.fromId(63, GameVersion.V1_20_50));
        assertEquals(63, ContainerSlotType.DYNAMIC_CONTAINER.getId(GameVersion.V1_21_20));
    }
}
