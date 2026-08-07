package cn.nukkit.block.util;

import cn.nukkit.block.*;
import cn.nukkit.level.Level;
import cn.nukkit.level.persistence.PersistentDataContainer;
import cn.nukkit.level.persistence.TestPersistentDataContainer;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedstoneToggleHelperTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("blockChanges")
    void blockChangeUpdatesManualOverride(String description, Block previous, Block current, boolean expectedOverride) {
        Level level = levelWithStorage(new TestPersistentDataContainer());
        place(previous, level, 10, 64, -5);
        place(current, level, 10, 64, -5);

        RedstoneToggleHelper.setManualOverride(level, 10, 64, -5, true);

        RedstoneToggleHelper.onBlockChanged(previous, current);

        assertEquals(expectedOverride, RedstoneToggleHelper.isManualOverride(level, 10, 64, -5));
    }

    static Stream<Arguments> blockChanges() {
        return Stream.of(
                Arguments.of("non-managed replacement clears", new BlockDoorWood(), new BlockStone(), false),
                Arguments.of("different managed type clears", new BlockDoorWood(), new BlockDoorIron(), false),
                Arguments.of("same block state preserves", new BlockFenceGate(), new BlockFenceGate(BlockFenceGate.OPEN_BIT), true),
                Arguments.of("copper variant preserves", new BlockTrapdoorCopper(), new BlockTrapdoorCopperExposed(), true)
        );
    }

    @Test
    void manualOverridePersistsAcrossReloadUntilCleared() {
        TestPersistentDataContainer originalStorage = new TestPersistentDataContainer();
        Level originalLevel = levelWithStorage(originalStorage);

        RedstoneToggleHelper.setManualOverride(originalLevel, 10, 64, -5, true);

        TestPersistentDataContainer reloadedStorage = originalStorage.copy();
        Level reloadedLevel = levelWithStorage(reloadedStorage);
        assertTrue(RedstoneToggleHelper.isManualOverride(reloadedLevel, 10, 64, -5));

        RedstoneToggleHelper.setManualOverride(reloadedLevel, 10, 64, -5, false);

        Level secondReload = levelWithStorage(reloadedStorage.copy());
        assertFalse(RedstoneToggleHelper.isManualOverride(secondReload, 10, 64, -5));
    }

    private static Block place(Block block, Level level, int x, int y, int z) {
        block.x = x;
        block.y = y;
        block.z = z;
        block.level = level;
        return block;
    }

    private static Level levelWithStorage(PersistentDataContainer storage) {
        Level level = mock(Level.class);
        when(level.getPersistentDataContainer(any(Vector3.class))).thenReturn(storage);
        return level;
    }
}
