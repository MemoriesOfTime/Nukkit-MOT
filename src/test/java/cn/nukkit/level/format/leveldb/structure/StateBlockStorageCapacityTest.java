package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.block.BlockID;
import cn.nukkit.level.util.BitArray;
import cn.nukkit.level.util.BitArrayVersion;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * V16 容量边界:调色板写满 65,536 项后写入新状态必须压缩腾位而非重入写锁死锁,
 * 且压缩重建后的插入点与位宽须按重建结果重算。
 * <p>
 * V16 capacity boundary: adding a state past a full 65,536-entry palette must compress for
 * room rather than deadlock on the non-reentrant write lock, then recompute insertion point and width.
 */
class StateBlockStorageCapacityTest {
    private static final int CELLS = 16 * 16 * 16;
    private static final int V16_CAPACITY = BitArrayVersion.V16.getMaxEntryValue() + 1;

    @Test
    void setPastAFullV16PaletteCompressesInsteadOfDeadlocking() throws Exception {
        ObjectArrayList<BlockStateSnapshot> palette = new ObjectArrayList<>(V16_CAPACITY);
        BlockStateSnapshot air = bareState(BlockID.AIR, 0);
        palette.add(air);
        for (int i = 1; i < V16_CAPACITY; i++) {
            palette.add(bareState(BlockID.DIRT, 0));
        }
        // Cells all reference entry zero; the dirt entries are unused churn.
        StateBlockStorage storage = new StateBlockStorage(BitArrayVersion.V16.createPalette(CELLS), palette, null, null);

        BlockStateSnapshot water = bareState(BlockID.WATER, 0);
        setAwaitingCompletion(storage, 123, water);

        assertSame(water, storage.getBlockState(123));
        assertSame(air, storage.getBlockState(122));
    }

    @Test
    void setPastAFullV16PaletteOfAllAirUsesTheSingletonSpecialCase() throws Exception {
        ObjectArrayList<BlockStateSnapshot> palette = new ObjectArrayList<>(V16_CAPACITY);
        BlockStateSnapshot air = bareState(BlockID.AIR, 0);
        palette.add(air);
        for (int i = 1; i < V16_CAPACITY; i++) {
            palette.add(bareState(BlockID.AIR, 0));
        }
        StateBlockStorage storage = new StateBlockStorage(BitArrayVersion.V16.createPalette(CELLS), palette, null, null);

        BlockStateSnapshot stone = bareState(BlockID.STONE, 0);
        setAwaitingCompletion(storage, 5, stone);

        assertSame(stone, storage.getBlockState(5));
        assertSame(air, storage.getBlockState(6));
    }

    @Test
    void setPastAFullV16PaletteWithEveryCellDistinctGrowsTheRebuiltWidth() throws Exception {
        // 4096 cells each referencing their own entry: compression cannot drop anything and the
        // rebuilt palette exactly fills V12, so the new entry only fits after another width growth.
        ObjectArrayList<BlockStateSnapshot> palette = new ObjectArrayList<>(V16_CAPACITY);
        for (int i = 0; i < CELLS; i++) {
            palette.add(bareState(1 + i % 250, i & 0xF));
        }
        for (int i = CELLS; i < V16_CAPACITY; i++) {
            palette.add(palette.get(1));
        }
        BitArray bits = BitArrayVersion.V16.createPalette(CELLS);
        for (int cell = 0; cell < CELLS; cell++) {
            bits.set(cell, cell);
        }
        StateBlockStorage storage = new StateBlockStorage(bits, palette, null, null);

        BlockStateSnapshot water = bareState(BlockID.WATER, 0);
        setAwaitingCompletion(storage, 0, water);

        assertSame(water, storage.getBlockState(0));
        for (int cell = 1; cell < CELLS; cell++) {
            assertSame(palette.get(cell), storage.getBlockState(cell), "cell " + cell);
        }
    }

    /**
     * set 越过写满 V16 调色板时若错误重入写锁会永久阻塞,故放到有界线程执行以便检测挂死。
     * <p>
     * A set past a full V16 palette deadlocks forever if the write lock is wrongly re-entered,
     * so the mutation runs on a bounded thread where a hang fails the test instead of the suite.
     */
    private static void setAwaitingCompletion(StateBlockStorage storage, int cell, BlockStateSnapshot state) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> done = pool.submit(() -> storage.set(cell, state));
            try {
                done.get(10, TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                fail("set() past a full V16 palette did not complete: write-lock re-entry");
            } catch (ExecutionException e) {
                throw new AssertionError("set() past a full V16 palette failed", e.getCause());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static BlockStateSnapshot bareState(int legacyId, int legacyData) {
        // Snapshots use identity equality: fresh instances are distinct palette entries.
        return BlockStateSnapshot.builder().legacyId(legacyId).legacyData(legacyData).build();
    }
}
