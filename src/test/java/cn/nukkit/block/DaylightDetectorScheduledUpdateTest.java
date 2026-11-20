package cn.nukkit.block;

import cn.nukkit.MockServer;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import cn.nukkit.scheduler.BlockUpdateScheduler;
import cn.nukkit.utils.BlockUpdateEntry;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DaylightDetectorScheduledUpdateTest {
    @BeforeAll static void initialize() { MockServer.init(); }

    @Test void placedDetectorStartsClock() {
        Level level = mock(Level.class);
        BlockDaylightDetector detector = detector(level, false);
        when(level.setBlock(detector, detector, true, true)).thenReturn(true);
        assertTrue(detector.place(Item.get(0), detector, detector, BlockFace.UP, 0, 0, 0, null));
        verify(level).scheduleUpdate(detector, 20);
    }

    @Test void rejectedPlacementDoesNotStartClock() {
        Level level = mock(Level.class);
        BlockDaylightDetector detector = detector(level, false);
        assertFalse(detector.place(Item.get(0), detector, detector, BlockFace.UP, 0, 0, 0, null));
        verify(level, never()).scheduleUpdate(any(Block.class), anyInt());
    }

    @Test void neighborUpdateRearmsExistingDetector() {
        Level level = mock(Level.class);
        BlockDaylightDetector detector = detector(level, false);
        detector.onUpdate(Level.BLOCK_UPDATE_NORMAL);
        verify(level).scheduleUpdate(detector, 20);
        verify(level, never()).updateAroundRedstone(any(), any());
    }

    @Test void scheduledUpdateRefreshesWireAndRearmsBothModes() {
        for (boolean inverted : new boolean[]{false, true}) {
            Level level = mock(Level.class);
            BlockDaylightDetector detector = detector(level, inverted);
            detector.onUpdate(Level.BLOCK_UPDATE_SCHEDULED);
            verify(level).updateAroundRedstone(detector, null);
            verify(level).scheduleUpdate(detector, 20);
        }
    }

    @Test void unrelatedUpdateDoesNotStartClock() {
        Level level = mock(Level.class);
        detector(level, false).onUpdate(Level.BLOCK_UPDATE_RANDOM);
        verifyNoInteractions(level);
    }

    @Test void bothModesRemainPowerSourcesDuringOffPhase() {
        Level level = mock(Level.class);
        BlockDaylightDetector normal = detector(level, false);
        BlockDaylightDetector inverted = detector(level, true);
        when(level.isAnimalSpawningAllowedByTime()).thenReturn(false);
        assertTrue(normal.isPowerSource());
        assertEquals(0, normal.getWeakPower(BlockFace.NORTH));
        assertEquals(15, inverted.getWeakPower(BlockFace.NORTH));
        when(level.isAnimalSpawningAllowedByTime()).thenReturn(true);
        assertTrue(inverted.isPowerSource());
        assertEquals(15, normal.getWeakPower(BlockFace.NORTH));
        assertEquals(0, inverted.getWeakPower(BlockFace.NORTH));
    }

    @Test void nativeSchedulerRefreshesOutputAfterDayChanges() {
        Level level = mock(Level.class);
        BlockDaylightDetector detector = detector(level, false);
        BlockUpdateScheduler scheduler = new BlockUpdateScheduler(level, 0);
        AtomicLong tick = new AtomicLong();
        when(level.isAreaLoaded(any())).thenReturn(true);
        when(level.getBlock(any(Vector3.class), anyInt())).thenReturn(detector);
        doAnswer(call -> {
            Block block = call.getArgument(0);
            BlockUpdateEntry entry = new BlockUpdateEntry(block.floor(), block,
                    tick.get() + (int) call.getArgument(1), 0);
            if (!scheduler.contains(entry)) scheduler.add(entry);
            return null;
        }).when(level).scheduleUpdate(any(Block.class), anyInt());

        when(level.isAnimalSpawningAllowedByTime()).thenReturn(true);
        detector.onUpdate(Level.BLOCK_UPDATE_NORMAL);
        assertEquals(1, scheduler.getPendingCount());
        detector.onUpdate(Level.BLOCK_UPDATE_NORMAL);
        assertEquals(1, scheduler.getPendingCount(), "neighbor updates do not multiply clocks");
        assertEquals(0, scheduler.tick(19));
        tick.set(20);
        when(level.isAnimalSpawningAllowedByTime()).thenReturn(false);
        assertEquals(1, scheduler.tick(20));
        verify(level).updateAroundRedstone(detector, null);
        assertEquals(0, detector.getWeakPower(BlockFace.NORTH));
        assertEquals(1, scheduler.getPendingCount());

        // A broken detector must not keep waking redstone or rearming its timer.
        when(level.getBlock(any(Vector3.class), anyInt())).thenReturn(new BlockAir());
        tick.set(40);
        assertEquals(1, scheduler.tick(40));
        assertEquals(0, scheduler.getPendingCount());
        verify(level, times(1)).updateAroundRedstone(detector, null);
    }

    private static BlockDaylightDetector detector(Level level, boolean inverted) {
        BlockDaylightDetector detector = inverted ? new BlockDaylightDetectorInverted() : new BlockDaylightDetector();
        detector.level = level;
        detector.x = 32;
        detector.y = 70;
        detector.z = -17;
        return detector;
    }
}
