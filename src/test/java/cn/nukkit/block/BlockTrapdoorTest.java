package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.util.RedstoneToggleHelper;
import cn.nukkit.level.Level;
import cn.nukkit.level.persistence.TestPersistentDataContainer;
import cn.nukkit.level.vibration.VibrationManager;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BlockTrapdoorTest {

    @Test
    void manualOverrideFollowsTrapdoorPowerLifecycle() {
        Level level = mockPoweredLevel(false, false, true, false);
        BlockTrapdoor trapdoor = createTrapdoor(level, 0);

        trapdoor.toggle(mock(Player.class));

        trapdoor.onUpdate(Level.BLOCK_UPDATE_REDSTONE);
        assertTrue(trapdoor.isOpen());
        assertTrue(RedstoneToggleHelper.isManualOverride(level, 10, 64, -5));

        trapdoor.onUpdate(Level.BLOCK_UPDATE_REDSTONE);
        assertTrue(trapdoor.isOpen());
        assertFalse(RedstoneToggleHelper.isManualOverride(level, 10, 64, -5));

        trapdoor.onUpdate(Level.BLOCK_UPDATE_REDSTONE);
        assertFalse(trapdoor.isOpen());
    }

    private static Level mockPoweredLevel(boolean... poweredValues) {
        Level level = mock(Level.class);
        Server server = mock(Server.class);
        PluginManager pluginManager = mock(PluginManager.class);
        VibrationManager vibrationManager = mock(VibrationManager.class);
        Boolean first = poweredValues[0];
        Boolean[] rest = new Boolean[poweredValues.length - 1];
        for (int i = 1; i < poweredValues.length; i++) {
            rest[i - 1] = poweredValues[i];
        }
        when(level.isBlockPowered(any(Vector3.class))).thenReturn(first, rest);
        when(level.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(level.getVibrationManager()).thenReturn(vibrationManager);
        when(level.getPersistentDataContainer(any(Vector3.class))).thenReturn(new TestPersistentDataContainer());
        return level;
    }

    private static BlockTrapdoor createTrapdoor(Level level, int meta) {
        BlockTrapdoor trapdoor = new BlockTrapdoor(meta);
        trapdoor.level = level;
        trapdoor.x = 10;
        trapdoor.y = 64;
        trapdoor.z = -5;
        return trapdoor;
    }
}
