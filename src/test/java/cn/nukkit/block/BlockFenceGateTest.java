package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.block.util.RedstoneToggleHelper;
import cn.nukkit.level.Level;
import cn.nukkit.level.persistence.TestPersistentDataContainer;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BlockFenceGateTest {

    @Test
    void manualOverrideFollowsFenceGatePowerLifecycle() {
        Level level = mock(Level.class, RETURNS_DEEP_STUBS);
        stubPersistentStorage(level);
        when(level.isBlockPowered(any(Vector3.class))).thenReturn(false, false, true, false);

        BlockFenceGate gate = new BlockFenceGate(0);
        gate.level = level;
        gate.x = 10;
        gate.y = 64;
        gate.z = -5;

        gate.toggle(mock(Player.class));

        gate.onUpdate(Level.BLOCK_UPDATE_REDSTONE);
        assertTrue(gate.isOpen());
        assertTrue(RedstoneToggleHelper.isManualOverride(level, 10, 64, -5));

        gate.onUpdate(Level.BLOCK_UPDATE_REDSTONE);
        assertTrue(gate.isOpen());
        assertFalse(RedstoneToggleHelper.isManualOverride(level, 10, 64, -5));

        gate.onUpdate(Level.BLOCK_UPDATE_REDSTONE);
        assertFalse(gate.isOpen());
    }

    private static void stubPersistentStorage(Level level) {
        when(level.getPersistentDataContainer(any(Vector3.class))).thenReturn(new TestPersistentDataContainer());
    }
}
