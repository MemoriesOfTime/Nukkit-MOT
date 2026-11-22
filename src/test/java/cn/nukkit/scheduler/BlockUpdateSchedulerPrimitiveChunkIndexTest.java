package cn.nukkit.scheduler;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockAir;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.utils.BlockUpdateEntry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class BlockUpdateSchedulerPrimitiveChunkIndexTest {
    private static final AxisAlignedBB WORLD = new SimpleAxisAlignedBB(
            -30000000, -1000, -30000000, 30000000, 1000, 30000000);

    @Test
    void diagonalChunksWithIdenticalBoxedHashesKeepIndependentTimelines() {
        BlockUpdateScheduler scheduler = new BlockUpdateScheduler(null, 0);
        Block block = new BlockAir();
        List<BlockUpdateEntry> entries = new ArrayList<>();
        for (int chunk = -512; chunk < 512; chunk++) {
            BlockUpdateEntry entry = entry(block, chunk, chunk, 2);
            scheduler.add(entry);
            entries.add(entry);
        }
        assertEquals(new HashSet<>(entries), scheduler.getPendingBlockUpdates(WORLD));
        for (int i = 0; i < entries.size(); i++) {
            BlockUpdateEntry entry = entries.get(i);
            assertEquals(Set.of(entry), scheduler.getPendingBlockUpdates(chunkArea(i - 512, i - 512)));
            if (i % 3 == 0) {
                assertTrue(scheduler.remove(entry));
                assertTrue(scheduler.getPendingBlockUpdates(chunkArea(i - 512, i - 512)).isEmpty());
                BlockUpdateEntry replacement = entry(block, i - 512, i - 512, 7);
                scheduler.add(replacement);
                assertEquals(Set.of(replacement), scheduler.getPendingBlockUpdates(chunkArea(i - 512, i - 512)));
                assertTrue(scheduler.remove(replacement));
            }
        }
        Set<BlockUpdateEntry> remaining = new HashSet<>();
        for (int i = 0; i < entries.size(); i++) {
            if (i % 3 != 0) remaining.add(entries.get(i));
        }
        assertEquals(remaining, scheduler.getPendingBlockUpdates(WORLD));
        for (BlockUpdateEntry entry : remaining) assertTrue(scheduler.remove(entry));
        assertTrue(scheduler.getPendingBlockUpdates(WORLD).isEmpty());
        assertEquals(0, scheduler.getPendingCount());
    }

    @Test
    void gridQueriesMatchCoordinateFilterAcrossBothIterationPaths() {
        BlockUpdateScheduler scheduler = new BlockUpdateScheduler(null, 0);
        Block block = new BlockAir();
        List<BlockUpdateEntry> entries = new ArrayList<>();
        for (int x = -16; x < 16; x++) {
            for (int z = -16; z < 16; z++) {
                BlockUpdateEntry entry = entry(block, x, z, 2 + ((x ^ z) & 3));
                scheduler.add(entry);
                entries.add(entry);
            }
        }
        AxisAlignedBB[] areas = {WORLD, chunkArea(-16, -16), chunkArea(0, 0), chunkArea(15, 15),
                new SimpleAxisAlignedBB(-200, -1000, -300, 200, 1000, 300),
                new SimpleAxisAlignedBB(-255.5, 0, -255.5, 16.5, 1, 16.5)};
        for (AxisAlignedBB area : areas) {
            Set<BlockUpdateEntry> expected = new HashSet<>();
            for (BlockUpdateEntry entry : entries) {
                if (entry.pos.x >= area.getMinX() && entry.pos.x < area.getMaxX()
                        && entry.pos.z >= area.getMinZ() && entry.pos.z < area.getMaxZ()) {
                    expected.add(entry);
                }
            }
            assertEquals(expected, scheduler.getPendingBlockUpdates(area));
        }
    }

    @Test
    void concurrentAddQueryAndRemoveRetainSchedulerMonitorProtection() throws Exception {
        BlockUpdateScheduler scheduler = new BlockUpdateScheduler(null, 0);
        Block block = new BlockAir();
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(4);
        List<java.util.concurrent.Future<List<BlockUpdateEntry>>> futures = new ArrayList<>();
        try {
            for (int worker = 0; worker < 4; worker++) {
                int origin = worker * 256;
                futures.add(pool.submit(() -> {
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    List<BlockUpdateEntry> kept = new ArrayList<>();
                    for (int i = 0; i < 256; i++) {
                        int chunk = origin + i;
                        BlockUpdateEntry entry = entry(block, chunk, chunk, 2);
                        scheduler.add(entry);
                        assertTrue(scheduler.getPendingBlockUpdates(chunkArea(chunk, chunk)).contains(entry));
                        if ((i & 1) == 0) assertTrue(scheduler.remove(entry));
                        else kept.add(entry);
                    }
                    return kept;
                }));
            }
            start.countDown();
            Set<BlockUpdateEntry> expected = new HashSet<>();
            for (var future : futures) expected.addAll(future.get(20, TimeUnit.SECONDS));
            assertEquals(expected, scheduler.getPendingBlockUpdates(WORLD));
            assertEquals(expected.size(), scheduler.getPendingCount());
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private static BlockUpdateEntry entry(Block block, int x, int z, long tick) {
        return new BlockUpdateEntry(new Vector3(x * 16 + 1, 64, z * 16 + 1), block, tick, 0);
    }

    private static AxisAlignedBB chunkArea(int x, int z) {
        return new SimpleAxisAlignedBB(x * 16, 0, z * 16, x * 16 + 16, 1, z * 16 + 16);
    }
}
