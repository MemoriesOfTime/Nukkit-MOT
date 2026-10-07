package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.ChunkSection;
import cn.nukkit.level.util.BitArrayVersion;
import org.cloudburstmc.nbt.NbtMap;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RandomTickPaletteTest {
    @Test void skipsAirStoneAndOreButNeverUniformTickingPalettes() {
        assertFalse(storage(BlockID.AIR).mayHaveRandomTickBlocks());
        assertFalse(storage(BlockID.STONE, BlockID.DIRT, BlockID.DIAMOND_ORE).mayHaveRandomTickBlocks());
        for (int id : new int[]{BlockID.GRASS, BlockID.WATER, BlockID.STILL_WATER,
                BlockID.WHEAT_BLOCK, BlockID.LEAVES, BlockID.FIRE, BlockID.ICE, BlockID.SNOW_LAYER}) {
            StateBlockStorage uniform = storage(id);
            assertTrue(uniform.isEmpty(), "legacy isEmpty cannot safely guard random ticks");
            assertTrue(uniform.mayHaveRandomTickBlocks(), "uniform ticking palette " + id);
        }
    }

    @Test void everyRegisteredTickingIdSurvivesTheProbeIncludingUnusedEntries() {
        for (int id = 0; id < Block.MAX_BLOCK_ID; id++) {
            assertEquals(Level.isRandomTickBlock(id), storage(id).mayHaveRandomTickBlocks(), "id=" + id);
        }
        assertFalse(Level.isRandomTickBlock(-1));
        assertFalse(Level.isRandomTickBlock(Block.MAX_BLOCK_ID));
        assertFalse(Level.isRandomTickBlock(Integer.MAX_VALUE));
    }

    @Test void insertionRemovalCompactionAndCopyCannotLeaveAStaleNegative() {
        StateBlockStorage data = storage(BlockID.AIR, BlockID.STONE);
        assertFalse(data.mayHaveRandomTickBlocks());
        data.set(123, state(BlockID.WHEAT_BLOCK));
        assertTrue(data.mayHaveRandomTickBlocks());
        StateBlockStorage copy = data.copy();
        assertTrue(copy.mayHaveRandomTickBlocks());
        data.set(123, state(BlockID.STONE));
        assertTrue(data.mayHaveRandomTickBlocks(), "unused entries stay conservative");
        data.compress();
        assertFalse(data.mayHaveRandomTickBlocks());
        assertTrue(copy.mayHaveRandomTickBlocks());
        data.set(17, state(BlockID.FIRE));
        assertTrue(data.mayHaveRandomTickBlocks());
    }

    @Test void largePalettesFallBackInsteadOfAddingAnUnboundedScan() {
        int[] boundary = new int[16];
        java.util.Arrays.fill(boundary, BlockID.STONE);
        assertFalse(storage(boundary).mayHaveRandomTickBlocks());
        boundary[15] = BlockID.GRASS;
        assertTrue(storage(boundary).mayHaveRandomTickBlocks());
        int[] ids = new int[17];
        java.util.Arrays.fill(ids, BlockID.STONE);
        assertTrue(storage(ids).mayHaveRandomTickBlocks());
        ids[16] = BlockID.GRASS;
        assertTrue(storage(ids).mayHaveRandomTickBlocks());
    }

    @Test void onlyLayerZeroIsSampledAndReplacingStorageIsVisibleImmediately() {
        LevelDBChunkSection section = new LevelDBChunkSection(0,
                new StateBlockStorage[]{storage(BlockID.AIR), storage(BlockID.WATER)}, false);
        assertFalse(section.mayHaveRandomTickBlocks());
        section.getStorages()[0] = storage(BlockID.GRASS);
        assertTrue(section.mayHaveRandomTickBlocks());
        section.getStorages()[0] = storage(BlockID.STONE);
        assertTrue(section.mayHaveRandomTickBlocks(), "positive memo is allowed to stay conservative");
        assertFalse(section.copy().mayHaveRandomTickBlocks(), "fresh section can rediscover inert data");
        ChunkSection unsupported = mock(ChunkSection.class, CALLS_REAL_METHODS);
        assertTrue(unsupported.mayHaveRandomTickBlocks(), "unknown providers retain old loop");
    }

    @Test void contendedWriterFallsBackWithoutWaitingOrClaimingSectionIsInert() throws Exception {
        LevelDBChunkSection section = new LevelDBChunkSection(0,
                new StateBlockStorage[]{storage(BlockID.AIR)}, false);
        CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1);
        Thread writer = new Thread(() -> {
            section.writeLock.lock();
            try { held.countDown(); release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { section.writeLock.unlock(); }
        });
        try {
            writer.start(); assertTrue(held.await(2, TimeUnit.SECONDS));
            assertTimeout(Duration.ofMillis(100), () -> assertTrue(section.mayHaveRandomTickBlocks()));
        } finally { release.countDown(); writer.join(2000); }
        assertFalse(section.mayHaveRandomTickBlocks());
    }

    private static StateBlockStorage storage(int... ids) {
        List<BlockStateSnapshot> palette = new ArrayList<>();
        for (int id : ids) palette.add(state(id));
        return new StateBlockStorage(BitArrayVersion.V2.createPalette(4096), palette, null, null);
    }
    private static BlockStateSnapshot state(int id) {
        return BlockStateSnapshot.builder().legacyId(id).legacyData(0).runtimeId(id)
                .vanillaState(NbtMap.builder().putString("name", "test:block_" + id)
                        .putCompound("states", NbtMap.EMPTY).putInt("version", 1).build()).build();
    }
}
