package cn.nukkit.network.protocol.types.inventory;

import cn.nukkit.GameVersion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 配方书容器槽位类型 (64-66) 的版本边界回归。
 * <p>
 * Recipe book container slot types (64-66) exist on the wire only since v944 (1.26.10);
 * Cloudburst Protocol inserts them into its type map at the same version. They must decode
 * to null and refuse to encode on every older protocol, including 1.21.20-1.26.0.
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
    void recipeContainersRefuseToEncodeBelowV1_26_10() {
        for (GameVersion version : new GameVersion[]{
                GameVersion.V1_19_80, GameVersion.V1_20_50, GameVersion.V1_21_20,
                GameVersion.V1_21_124, GameVersion.V1_26_0}) {
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
    void dynamicContainerBoundaryIsUnchanged() {
        assertEquals(ContainerSlotType.DYNAMIC_CONTAINER,
                ContainerSlotType.fromId(63, GameVersion.V1_21_20));
        assertEquals(ContainerSlotType.DYNAMIC_CONTAINER,
                ContainerSlotType.fromId(63, GameVersion.V1_26_0));
        assertNull(ContainerSlotType.fromId(63, GameVersion.V1_20_50));
        assertEquals(63, ContainerSlotType.DYNAMIC_CONTAINER.getId(GameVersion.V1_21_20));
    }
}
