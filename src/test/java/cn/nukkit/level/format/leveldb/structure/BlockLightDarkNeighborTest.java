package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.utils.Zlib;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BlockLightDarkNeighborTest {
    private static final int[][] NEIGHBORS = {
            {7, 8, 8}, {9, 8, 8}, {8, 7, 8},
            {8, 9, 8}, {8, 8, 7}, {8, 8, 9}
    };

    @BeforeAll static void initializeBlocks() { Block.init(); }

    @Test void darkAndUnitLightKeepTheEmitterButSkipSixPaletteReads() throws Exception {
        for (int light : new int[]{0, 1}) {
            StateBlockStorage storage = new StateBlockStorage();
            LevelDBChunkSection section = new LevelDBChunkSection(0,
                    new StateBlockStorage[]{storage}, false);
            storage.set(8, 8, 8, BlockStateSnapshot.builder()
                    .legacyId(BlockID.GLOWSTONE).legacyData(0).build());
            section.setBlockLight(8, 8, 8, 3);
            for (int[] neighbor : NEIGHBORS) {
                section.setBlockLight(neighbor[0], neighbor[1], neighbor[2], light);
            }
            CountingPalette palette = countReads(storage);
            assertEquals((3L << 32) | Block.getBlockLight(BlockID.GLOWSTONE),
                    section.sampleInteriorBlockLight(8, 8, 8));
            assertEquals(1, palette.reads, "Only the center emitter needs its palette entry");
        }
    }

    @Test void mixedLightReadsExactlyTheThreeContributingNeighbors() throws Exception {
        StateBlockStorage storage = new StateBlockStorage();
        LevelDBChunkSection section = new LevelDBChunkSection(0,
                new StateBlockStorage[]{storage}, false);
        int[] light = {0, 1, 2, 15, 0, 3};
        for (int i = 0; i < NEIGHBORS.length; i++) {
            int[] neighbor = NEIGHBORS[i];
            section.setBlockLight(neighbor[0], neighbor[1], neighbor[2], light[i]);
        }
        CountingPalette palette = countReads(storage);
        assertEquals(14L, section.sampleInteriorBlockLight(8, 8, 8));
        assertEquals(4, palette.reads, "Center plus three neighbors above unit light");
    }

    @Test void brightAndUnknownNeighborsRetainTheExistingFiltering() throws Exception {
        StateBlockStorage storage = new StateBlockStorage();
        LevelDBChunkSection section = new LevelDBChunkSection(0,
                new StateBlockStorage[]{storage}, false);
        for (int i = 0; i < NEIGHBORS.length; i++) {
            int[] neighbor = NEIGHBORS[i];
            storage.set(neighbor[0], neighbor[1], neighbor[2], BlockStateSnapshot.builder()
                    .legacyId(i % 2 == 0 ? -1 : Block.MAX_BLOCK_ID).legacyData(0).build());
            section.setBlockLight(neighbor[0], neighbor[1], neighbor[2], 15);
        }
        CountingPalette palette = countReads(storage);
        assertEquals(0L, section.sampleInteriorBlockLight(8, 8, 8));
        assertEquals(7, palette.reads, "Bright neighbors must still read and validate every filter");
    }

    @Test void customStorageStillRunsBeforeItsNeighborLightIsRead() {
        LevelDBChunkSection[] owner = new LevelDBChunkSection[1];
        int[] reads = {0};
        StateBlockStorage custom = new StateBlockStorage() {
            @Override public BlockStateSnapshot getBlockState(int x, int y, int z) {
                reads[0]++;
                if (x == 7 && y == 8 && z == 8) owner[0].setBlockLight(x, y, z, 15);
                return super.getBlockState(x, y, z);
            }
        };
        owner[0] = new LevelDBChunkSection(0, new StateBlockStorage[]{custom}, false);
        assertEquals(14L, owner[0].sampleInteriorBlockLight(8, 8, 8));
        assertEquals(7, reads[0], "Public storage overrides retain all six neighbor calls");
    }

    @Test void firstSampleInflatesUnitLightBeforeSkippingPaletteReads() throws Exception {
        StateBlockStorage storage = new StateBlockStorage();
        LevelDBChunkSection section = new LevelDBChunkSection(0,
                new StateBlockStorage[]{storage}, false);
        byte[] packed = new byte[2048];
        Arrays.fill(packed, (byte) 0x11);
        section.hasBlockLight = true;
        section.compressedLight = Zlib.deflate(packed);
        CountingPalette palette = countReads(storage);
        assertNull(section.blockLight);
        assertEquals(1L << 32, section.sampleInteriorBlockLight(8, 8, 8));
        assertEquals(1, palette.reads);
        assertArrayEquals(packed, section.getLightArray());
    }

    @Test void absentPrimaryLayerRetainsAirAttenuation() {
        LevelDBChunkSection section = new LevelDBChunkSection(0,
                new StateBlockStorage[]{null, new StateBlockStorage()}, false);
        assertEquals(0L, section.sampleInteriorBlockLight(8, 8, 8));
        section.setBlockLight(7, 8, 8, 15);
        assertEquals(14L, section.sampleInteriorBlockLight(8, 8, 8));
    }

    @SuppressWarnings("unchecked")
    private static CountingPalette countReads(StateBlockStorage storage) throws Exception {
        assertEquals(StateBlockStorage.class, storage.getClass());
        Field field = StateBlockStorage.class.getDeclaredField("palette");
        field.setAccessible(true);
        CountingPalette palette = new CountingPalette((List<BlockStateSnapshot>) field.get(storage));
        field.set(storage, palette);
        return palette;
    }

    private static final class CountingPalette extends AbstractList<BlockStateSnapshot> {
        final List<BlockStateSnapshot> delegate;
        int reads;

        CountingPalette(List<BlockStateSnapshot> delegate) { this.delegate = delegate; }

        @Override public BlockStateSnapshot get(int index) {
            reads++;
            return delegate.get(index);
        }

        @Override public int size() { return delegate.size(); }
    }
}
