package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.utils.Zlib;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.*;

class BlockLightInteriorSampleTest {
    @BeforeAll static void blocks() { Block.init(); }

    private static long scalar(LevelDBChunkSection section, int x, int y, int z) {
        int current = section.getBlockLight(x, y, z);
        int expected = Block.getBlockLight(section.getBlockId(x, y, z));
        int[][] offsets = {{-1,0,0},{1,0,0},{0,-1,0},{0,1,0},{0,0,-1},{0,0,1}};
        for (int[] d : offsets) {
            int sx=x+d[0], sy=y+d[1], sz=z+d[2];
            int id = section.getBlockId(sx, sy, sz);
            int filter = id < 0 || id >= Block.MAX_BLOCK_ID ? 15 : Block.lightFilter[id];
            expected = Math.max(expected, Math.max(0, section.getBlockLight(sx, sy, sz) - Math.max(1, filter)));
        }
        return ((long) current << 32) | (expected & 0xffffffffL);
    }

    @Test void equalsSevenScalarReadsForEveryInteriorIncludingCompressedLight() throws Exception {
        StateBlockStorage storage = new StateBlockStorage();
        LevelDBChunkSection section = new LevelDBChunkSection(-1, new StateBlockStorage[]{storage}, false);
        Random random = new Random(319323324);
        int[] ids = {BlockID.AIR, BlockID.STONE, BlockID.LEAVES, BlockID.GLASS,
                BlockID.STILL_WATER, BlockID.TORCH, BlockID.GLOWSTONE};
        byte[] packed = new byte[2048];
        random.nextBytes(packed);
        section.hasBlockLight = true;
        section.compressedLight = Zlib.deflate(packed);
        for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
            storage.set(x, y, z, BlockStateSnapshot.builder().legacyId(ids[random.nextInt(ids.length)]).legacyData(0).build());
        for (int y = 1; y < 15; y++) for (int z = 1; z < 15; z++) for (int x = 1; x < 15; x++)
            assertEquals(scalar(section, x, y, z), section.sampleInteriorBlockLight(x, y, z));
        assertArrayEquals(packed, section.getLightArray(), "sampling does not replace light values");
    }

    @Test void liveReadLockIsTakenOnceAndBlocksBehindAWriter() throws Exception {
        LevelDBChunkSection section = new LevelDBChunkSection(0);
        Lock delegate = section.readLock;
        CountDownLatch readStarted = new CountDownLatch(1);
        int[] counts = new int[2];
        section.readLock = new Lock() {
            public void lock() { counts[0]++; readStarted.countDown(); delegate.lock(); }
            public void unlock() { counts[1]++; delegate.unlock(); }
            public void lockInterruptibly() throws InterruptedException { delegate.lockInterruptibly(); }
            public boolean tryLock() { return delegate.tryLock(); }
            public boolean tryLock(long n, TimeUnit u) throws InterruptedException { return delegate.tryLock(n,u); }
            public Condition newCondition() { return delegate.newCondition(); }
        };
        ExecutorService worker = Executors.newSingleThreadExecutor();
        section.writeLock.lock();
        Future<Long> sample;
        try {
            sample = worker.submit(() -> section.sampleInteriorBlockLight(8, 8, 8));
            assertTrue(readStarted.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> sample.get(100, TimeUnit.MILLISECONDS));
            section.setBlockLight(7, 8, 8, 15);
        } finally { section.writeLock.unlock(); }
        try {
            assertEquals(14L, sample.get(5, TimeUnit.SECONDS));
            assertEquals(1, counts[0]);
            assertEquals(1, counts[1]);
        } finally { worker.shutdownNow(); }
    }

    @Test void facesAndVirtualSectionsRequireScalarFallback() {
        LevelDBChunkSection section = new LevelDBChunkSection(0);
        assertThrows(IllegalArgumentException.class, () -> section.sampleInteriorBlockLight(0,8,8));
        assertThrows(IllegalArgumentException.class, () -> section.sampleInteriorBlockLight(8,15,8));
        LevelDBChunkSection override = new LevelDBChunkSection(0) {
            @Override public int getBlockId(int x, int y, int z) { return BlockID.GLOWSTONE; }
        };
        assertThrows(IllegalArgumentException.class, () -> override.sampleInteriorBlockLight(8,8,8));
    }

    @Test void packingDoesNotChangeEmitterValuesAndUnknownFilters() {
        StateBlockStorage storage = new StateBlockStorage();
        LevelDBChunkSection section = new LevelDBChunkSection(0, new StateBlockStorage[]{storage}, false);
        storage.set(8,8,8,BlockStateSnapshot.builder().legacyId(BlockID.GLOWSTONE).legacyData(0).build());
        storage.set(7,8,8,BlockStateSnapshot.builder().legacyId(-1).legacyData(0).build());
        storage.set(9,8,8,BlockStateSnapshot.builder().legacyId(Block.MAX_BLOCK_ID).legacyData(0).build());
        section.setBlockLight(7,8,8,15);
        section.setBlockLight(9,8,8,15);
        section.setBlockLight(8,8,8,3);
        int original = Block.light[BlockID.GLOWSTONE];
        try {
            Block.light[BlockID.GLOWSTONE] = 31;
            assertEquals(scalar(section,8,8,8), section.sampleInteriorBlockLight(8,8,8));
            assertEquals((3L << 32) | 31, section.sampleInteriorBlockLight(8,8,8));
        } finally { Block.light[BlockID.GLOWSTONE] = original; }
    }
}
