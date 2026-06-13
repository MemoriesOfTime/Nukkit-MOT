package cn.nukkit.block;

import cn.nukkit.level.Level;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BlockDispenserTest {

    @Test
    void doesNotRetriggerWhileContinuousSignalStaysPoweredDuringDispense() {
        Level level = mock(Level.class);
        when(level.isBlockPowered(any(Vector3.class))).thenReturn(true);

        TestDispenser dispenser = createDispenser(level);

        dispenser.onUpdate(Level.BLOCK_UPDATE_REDSTONE);

        verify(level, times(1)).scheduleUpdate(same(dispenser), same(dispenser), eq(4));
        assertTrue(dispenser.isTriggered());

        dispenser.duringDispense = () -> dispenser.onUpdate(Level.BLOCK_UPDATE_NORMAL);
        dispenser.onUpdate(Level.BLOCK_UPDATE_SCHEDULED);

        assertEquals(1, dispenser.dispenseCalls);
        assertTrue(dispenser.isTriggered());
        verify(level, times(1)).scheduleUpdate(same(dispenser), same(dispenser), eq(4));
    }

    @Test
    void clearsTriggeredLatchWhenSignalTurnsOff() {
        Level level = mock(Level.class);
        when(level.isBlockPowered(any(Vector3.class))).thenReturn(true, false, false);

        TestDispenser dispenser = createDispenser(level);

        dispenser.onUpdate(Level.BLOCK_UPDATE_REDSTONE);
        assertTrue(dispenser.isTriggered());

        dispenser.onUpdate(Level.BLOCK_UPDATE_REDSTONE);

        assertFalse(dispenser.isTriggered());
        verify(level, times(1)).scheduleUpdate(same(dispenser), same(dispenser), eq(4));
    }

    private static TestDispenser createDispenser(Level level) {
        TestDispenser dispenser = new TestDispenser();
        dispenser.level = level;
        dispenser.x = 0;
        dispenser.y = 64;
        dispenser.z = 0;
        dispenser.setDamage(BlockFace.NORTH.getIndex());
        return dispenser;
    }

    private static final class TestDispenser extends BlockDispenser {
        private int dispenseCalls;
        private Runnable duringDispense;

        @Override
        public void dispense() {
            dispenseCalls++;
            if (duringDispense != null) {
                duringDispense.run();
            }
        }
    }
}
