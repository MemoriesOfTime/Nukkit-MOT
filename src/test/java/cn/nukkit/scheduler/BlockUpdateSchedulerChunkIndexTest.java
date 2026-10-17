package cn.nukkit.scheduler;

import cn.nukkit.block.Block;
import cn.nukkit.level.Level;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.utils.BlockUpdateEntry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BlockUpdateSchedulerChunkIndexTest {
    private static Block block(int id) {
        Block block = mock(Block.class);
        when(block.getId()).thenReturn(id);
        return block;
    }

    private static BlockUpdateEntry add(BlockUpdateScheduler scheduler, Block block,
                                        double x, double y, double z, long tick) {
        BlockUpdateEntry entry = new BlockUpdateEntry(new Vector3(x, y, z), block, tick, 0);
        scheduler.add(entry);
        return entry;
    }

    @Test
    void preservesMotsShifted18By18RegionAndIgnoresHeight() {
        BlockUpdateScheduler scheduler = new BlockUpdateScheduler(mock(Level.class), 0);
        Block block = block(1);
        BlockUpdateEntry corner = add(scheduler, block, -2, -1000, -2, 2);
        BlockUpdateEntry inside = add(scheduler, block, 15, 1000, 15, 3);
        add(scheduler, block, -3, 64, 0, 4);
        add(scheduler, block, 16, 64, 0, 5);
        add(scheduler, block, 0, 64, 16, 6);
        add(scheduler, block, 0, 64, -3, 7);
        assertEquals(Set.of(corner, inside), scheduler.getPendingBlockUpdates(
                new SimpleAxisAlignedBB(-2, 0, -2, 16, 319, 16)));
        assertTrue(scheduler.getPendingBlockUpdates(new SimpleAxisAlignedBB(0, 0, 0, 0, 319, 16)).isEmpty());
    }

    @Test
    void floorsNegativeChunksAndRetainsFractionalAabbEdges() {
        BlockUpdateScheduler scheduler = new BlockUpdateScheduler(mock(Level.class), 0);
        Block block = block(1);
        BlockUpdateEntry negative = add(scheduler, block, -18, 64, -18, 2);
        add(scheduler, block, 0, 64, -18, 2);
        assertEquals(Set.of(negative), scheduler.getPendingBlockUpdates(
                new SimpleAxisAlignedBB(-18, 0, -18, 0, 319, 0)));
        BlockUpdateEntry fractional = add(scheduler, block, 15.75, 64, 15.75, 3);
        add(scheduler, block, 16, 64, 15.75, 3);
        assertEquals(Set.of(fractional), scheduler.getPendingBlockUpdates(
                new SimpleAxisAlignedBB(15.5, 0, 15.5, 16, 319, 16)));
    }

    @Test
    void smallSaveAreaDoesNotVisitUpdatesInOtherChunks() {
        BlockUpdateScheduler scheduler = new BlockUpdateScheduler(mock(Level.class), 0);
        Block block = block(1);
        BlockUpdateEntry local = add(scheduler, block, 0, 64, 0, 2);
        List<Vector3> remote = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Vector3 pos = spy(new Vector3(320 + i * 16, 64, 320));
            scheduler.add(new BlockUpdateEntry(pos, block, 2, 0));
            clearInvocations(pos);
            remote.add(pos);
        }
        assertEquals(Set.of(local), scheduler.getPendingBlockUpdates(
                new SimpleAxisAlignedBB(-2, 0, -2, 16, 319, 16)));
        for (Vector3 pos : remote) {
            verify(pos, never()).getX();
            verify(pos, never()).getZ();
        }
        assertEquals(101, scheduler.getPendingBlockUpdates(
                new SimpleAxisAlignedBB(-30000000, 0, -30000000, 30000000, 319, 30000000)).size());
    }

    @Test
    void deduplicatesSnapshotsAndKeepsAnotherTickAfterCancellation() {
        BlockUpdateScheduler scheduler = new BlockUpdateScheduler(mock(Level.class), 0);
        Block block = block(1);
        BlockUpdateEntry first = add(scheduler, block, 0, 64, 0, 2);
        add(scheduler, block, 0, 64, 0, 3);
        AxisAlignedBB area = new SimpleAxisAlignedBB(-2, 0, -2, 16, 319, 16);
        assertEquals(1, scheduler.getPendingBlockUpdates(area).size());
        assertTrue(scheduler.remove(first));
        assertEquals(1, scheduler.getPendingBlockUpdates(area).size());
        assertTrue(scheduler.remove(first));
        assertTrue(scheduler.getPendingBlockUpdates(area).isEmpty());
        assertFalse(scheduler.remove(first));
    }

    @Test
    void futureIndexActiveGuardAndReentrantSchedulingKeepMotsSemantics() {
        Level level = mock(Level.class);
        BlockUpdateScheduler scheduler = new BlockUpdateScheduler(level, 0);
        Block block = block(1);
        Vector3 pos = new Vector3(0, 64, 0);
        AxisAlignedBB area = new SimpleAxisAlignedBB(-2, 0, -2, 16, 319, 16);
        when(level.isAreaLoaded(any())).thenReturn(true);
        when(level.getBlock(pos, 0)).thenReturn(block);
        doAnswer(invocation -> {
            assertTrue(scheduler.isBlockTickPending(pos, block));
            assertFalse(scheduler.contains(new BlockUpdateEntry(pos, block)));
            assertTrue(scheduler.getPendingBlockUpdates(area).isEmpty());
            scheduler.add(new BlockUpdateEntry(pos, block, 1, 0));
            assertEquals(1, scheduler.getPendingBlockUpdates(area).size());
            return 0;
        }).when(block).onUpdate(Level.BLOCK_UPDATE_SCHEDULED);
        scheduler.add(new BlockUpdateEntry(pos, block, 1, 0));
        assertEquals(1, scheduler.tick(1));
        assertEquals(1, scheduler.getPendingCount());
        assertEquals(1, scheduler.tick(2));
        verify(block, times(2)).onUpdate(Level.BLOCK_UPDATE_SCHEDULED);
    }

    @Test
    void unloadedRequeueAndLongSkippedTickRetainIndexedUpdates() {
        Level level = mock(Level.class);
        BlockUpdateScheduler scheduler = new BlockUpdateScheduler(level, 0);
        Block block = block(1);
        Vector3 pos = new Vector3(-1, 64, -1);
        when(level.isAreaLoaded(any())).thenReturn(false);
        doAnswer(invocation -> {
            scheduler.add(new BlockUpdateEntry(pos, block, 1, 0));
            return null;
        }).when(level).scheduleUpdate(block, pos, 0);
        scheduler.add(new BlockUpdateEntry(pos, block, 1, 0));
        assertEquals(1, scheduler.tick(100000));
        assertEquals(1, scheduler.getPendingCount());
        assertEquals(1, scheduler.getPendingBlockUpdates(new SimpleAxisAlignedBB(-2, 0, -2, 16, 319, 16)).size());
        verify(level).scheduleUpdate(block, pos, 0);
    }
}
