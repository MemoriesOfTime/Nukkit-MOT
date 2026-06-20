package cn.nukkit.block;

import cn.nukkit.MockServer;
import cn.nukkit.blockentity.BlockEntitySculkSensor;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import cn.nukkit.scheduler.BlockUpdateScheduler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Drives a real {@link BlockUpdateScheduler} to verify the sensor cycles
 * INACTIVE -> ACTIVE -> COOLDOWN -> INACTIVE and resets its power.
 */
public class SculkSensorPhaseTransitionTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @Test
    void sensorCyclesBackToInactiveAfterTrigger() {
        assertPhaseCycle(new TestSculkSensor(), BlockSculkSensor.ACTIVE_TICKS);
    }

    private void assertPhaseCycle(TestSensorBase block, int activeTicks) {
        Level level = mock(Level.class);
        block.x = 0;
        block.y = 64;
        block.z = 0;
        block.level = level;

        // Mock block entity tracking power, so we can assert it resets after the active phase.
        AtomicInteger power = new AtomicInteger(0);
        BlockEntitySculkSensor be = mock(BlockEntitySculkSensor.class);
        doAnswer(inv -> power.get()).when(be).getPower();
        doAnswer(inv -> {
            power.set(inv.getArgument(0));
            return null;
        }).when(be).setPower(anyInt());
        doAnswer(inv -> {
            power.set(0);
            return null;
        }).when(be).resetPower();
        block.be = be;

        // level.getBlock always reflects the current block (with current phase).
        when(level.getBlock(any(Vector3.class), anyInt())).thenReturn(block);
        when(level.getBlock(any(Vector3.class))).thenReturn(block);

        // Route scheduleUpdate calls into a real scheduler.
        BlockUpdateScheduler scheduler = new BlockUpdateScheduler(level, 0L);
        AtomicReference<long[]> currentTickHolder = new AtomicReference<>(new long[]{0L});

        // Mirror Level.scheduleUpdate: skip if the scheduler already has an entry at this pos.
        lenient().doAnswer(inv -> {
            Block b = inv.getArgument(0);
            Vector3 pos = inv.getArgument(1);
            int delay = inv.getArgument(2);
            addEntry(scheduler, b, pos, delay, currentTickHolder);
            return null;
        }).when(level).scheduleUpdate(any(Block.class), any(Vector3.class), anyInt(), anyInt(), anyBoolean());
        lenient().doAnswer(inv -> {
            Block b = inv.getArgument(0);
            int delay = inv.getArgument(1);
            addEntry(scheduler, b, new Vector3(b.x, b.y, b.z), delay, currentTickHolder);
            return null;
        }).when(level).scheduleUpdate(any(Block.class), anyInt());

        // Keep perform()'s reschedule-on-unload path inert.
        when(level.isAreaLoaded(any())).thenReturn(true);
        doAnswer(inv -> null).when(level).updateAroundRedstone(any(Vector3.class), any());

        // Trigger activation (as if a vibration arrived).
        block.activate(10);
        assertEquals(BlockSculkSensor.PHASE_ACTIVE, block.getPhase());
        assertEquals(10, power.get(), "power should be set to the calculated value while active");

        // Advance past the active phase -> COOLDOWN, power reset to 0.
        tick(scheduler, currentTickHolder, activeTicks);
        assertEquals(BlockSculkSensor.PHASE_COOLDOWN, block.getPhase(),
                "phase should be COOLDOWN after active ticks");
        assertEquals(0, power.get(), "power must be reset when leaving the active phase");

        // Advance past COOLDOWN_TICKS -> INACTIVE.
        tick(scheduler, currentTickHolder, BlockSculkSensor.COOLDOWN_TICKS);
        assertEquals(BlockSculkSensor.PHASE_INACTIVE, block.getPhase(),
                "phase should return to INACTIVE after cooldown");
    }

    private static void tick(BlockUpdateScheduler scheduler, AtomicReference<long[]> tickHolder, int ticks) {
        for (int i = 0; i < ticks; i++) {
            tickHolder.get()[0]++;
            scheduler.tick(tickHolder.get()[0]);
        }
    }

    /** Adds an entry only when the scheduler doesn't already track this pos. */
    private static void addEntry(BlockUpdateScheduler scheduler, Block b, Vector3 pos, int delay,
                                 AtomicReference<long[]> currentTickHolder) {
        cn.nukkit.utils.BlockUpdateEntry entry = new cn.nukkit.utils.BlockUpdateEntry(
                pos.floor(), b, currentTickHolder.get()[0] + delay, 0);
        if (!scheduler.contains(entry)) {
            scheduler.add(entry);
        }
    }

    /** BlockSculkSensor subclass returning a pre-set mock block entity. */
    private abstract static class TestSensorBase extends BlockSculkSensor {
        BlockEntitySculkSensor be;

        @Override
        public BlockEntitySculkSensor getOrCreateBlockEntity() {
            return be;
        }

        @Override
        public boolean isWaterlogged() {
            return false;
        }
    }

    private static class TestSculkSensor extends TestSensorBase {
    }
}
