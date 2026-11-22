package cn.nukkit.level.structure;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.BlockPalette;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.math.BlockVector3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;

/**
 * 抓取语义：结构空位（structure void）应落成 -1，而不是普通方块条目。
 * <p>
 * Capture semantics: structure void must become {@code -1} instead of a palette entry.
 */
class StructureCaptureTest {

    @BeforeAll
    static void setUp() {
        MockServer.init();
    }

    @Test
    void savesAndLoadsThroughDiskWithoutLosingBlocks() throws Exception {
        Level level = Mockito.mock(Level.class);
        BaseFullChunk chunk = Mockito.mock(BaseFullChunk.class);
        Mockito.when(level.getMinBlockY()).thenReturn(-64);
        Mockito.when(level.getMaxBlockY()).thenReturn(320);
        Mockito.when(level.getChunk(anyInt(), anyInt())).thenReturn(chunk);
        Mockito.when(level.getChunkBlockEntities(anyInt(), anyInt())).thenReturn(Map.of());
        Mockito.when(chunk.getBlockId(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(BlockID.AIR);
        Mockito.when(chunk.getBlockData(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(0);
        Mockito.when(chunk.getBlockId(0, 64, 0, 0)).thenReturn(BlockID.STONE);
        Mockito.when(chunk.getBlockId(1, 64, 0, 0)).thenReturn(BlockID.DIRT);
        Mockito.when(chunk.getBlockId(2, 64, 0, 0)).thenReturn(BlockID.WOODEN_STAIRS);

        Structure captured = StructureCapture.capture(level,
                new StructureBox(new BlockVector3(0, 64, 0), new BlockVector3(3, 1, 1)), false);
        String name = "structtest:" + System.nanoTime();
        java.io.File file = new java.io.File(System.getProperty("java.io.tmpdir"),
                "structures/structtest/" + name.substring(name.indexOf(':') + 1) + ".mcstructure");
        try {
            org.junit.jupiter.api.Assertions.assertTrue(StructureAPI.save(captured, name));
            Structure loaded = StructureAPI.load(name);
            assertNotNull(loaded);
            int[] expected = {
                    BlockID.STONE << Block.DATA_BITS,
                    BlockID.DIRT << Block.DATA_BITS,
                    BlockID.WOODEN_STAIRS << Block.DATA_BITS
            };
            for (int x = 0; x < expected.length; x++) {
                assertEquals(expected[x], loaded.getLegacyId(loaded.getBlockIndex(0, loaded.index(x, 0, 0))),
                        "disk round trip lost the legacy id of block " + expected[x]);
            }
        } finally {
            java.nio.file.Files.deleteIfExists(file.toPath());
        }
    }

    @Test
    void captureToNbtRoundTripKeepsLegacyIds() {
        Level level = Mockito.mock(Level.class);
        BaseFullChunk chunk = Mockito.mock(BaseFullChunk.class);
        Mockito.when(level.getMinBlockY()).thenReturn(-64);
        Mockito.when(level.getMaxBlockY()).thenReturn(320);
        Mockito.when(level.getChunk(anyInt(), anyInt())).thenReturn(chunk);
        Mockito.when(level.getChunkBlockEntities(anyInt(), anyInt())).thenReturn(Map.of());
        Mockito.when(chunk.getBlockId(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(BlockID.AIR);
        Mockito.when(chunk.getBlockData(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(0);
        Mockito.when(chunk.getBlockId(0, 64, 0, 0)).thenReturn(BlockID.STONE);
        Mockito.when(chunk.getBlockId(1, 64, 0, 0)).thenReturn(BlockID.DIRT);
        Mockito.when(chunk.getBlockId(2, 64, 0, 0)).thenReturn(BlockID.WOODEN_STAIRS);
        Mockito.when(chunk.getBlockId(3, 64, 0, 0)).thenReturn(BlockID.AIR);

        Structure captured = StructureCapture.capture(level,
                new StructureBox(new BlockVector3(0, 64, 0), new BlockVector3(4, 1, 1)), false);
        int[] expected = {
                BlockID.STONE << Block.DATA_BITS,
                BlockID.DIRT << Block.DATA_BITS,
                BlockID.WOODEN_STAIRS << Block.DATA_BITS,
                BlockID.AIR << Block.DATA_BITS
        };
        for (int x = 0; x < expected.length; x++) {
            assertEquals(expected[x], captured.getLegacyId(captured.getBlockIndex(0, captured.index(x, 0, 0))),
                    "capture lost the legacy id of block " + expected[x]);
        }

        BlockPalette palette = GlobalBlockPalette.getPaletteByProtocol(GameVersion.getFeatureVersion());
        Structure loaded = StructureIO.fromNbt(StructureIO.toNbt(captured), palette);
        for (int x = 0; x < expected.length; x++) {
            assertEquals(expected[x], loaded.getLegacyId(loaded.getBlockIndex(0, loaded.index(x, 0, 0))),
                    "nbt round trip lost the legacy id of block " + expected[x]);
        }
    }

    @Test
    void structureBlockBecomesAirWithoutItsModeMeta() {
        Level level = Mockito.mock(Level.class);
        BaseFullChunk chunk = Mockito.mock(BaseFullChunk.class);
        Mockito.when(level.getMinBlockY()).thenReturn(-64);
        Mockito.when(level.getMaxBlockY()).thenReturn(320);
        Mockito.when(level.getChunk(anyInt(), anyInt())).thenReturn(chunk);
        Mockito.when(level.getChunkBlockEntities(anyInt(), anyInt())).thenReturn(Map.of());
        Mockito.when(chunk.getBlockId(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(BlockID.AIR);
        Mockito.when(chunk.getBlockData(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(0);
        Mockito.when(chunk.getBlockId(0, 64, 0, 0)).thenReturn(BlockID.STRUCTURE_BLOCK);
        Mockito.when(chunk.getBlockData(0, 64, 0, 0)).thenReturn(3);

        Structure structure = StructureCapture.capture(level,
                new StructureBox(new BlockVector3(0, 64, 0), new BlockVector3(1, 1, 1)), false);
        int index = structure.getBlockIndex(0, structure.index(0, 0, 0));
        assertEquals(BlockID.AIR << Block.DATA_BITS, structure.getLegacyId(index),
                "the structure block's mode meta must not leak into the air entry");
        assertEquals(1, structure.getPalette().size(),
                "no info_update entry may be added for the structure block itself");
    }

    @Test
    void waterloggedSecondLayerSurvivesCaptureAndFileRoundTrip() {
        Level level = Mockito.mock(Level.class);
        BaseFullChunk chunk = Mockito.mock(BaseFullChunk.class);
        Mockito.when(level.getMinBlockY()).thenReturn(-64);
        Mockito.when(level.getMaxBlockY()).thenReturn(320);
        Mockito.when(level.getChunk(anyInt(), anyInt())).thenReturn(chunk);
        Mockito.when(level.getChunkBlockEntities(anyInt(), anyInt())).thenReturn(Map.of());
        Mockito.when(chunk.getBlockId(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(BlockID.AIR);
        Mockito.when(chunk.getBlockData(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(0);
        Mockito.when(chunk.getBlockId(0, 64, 0, 0)).thenReturn(BlockID.STONE);
        Mockito.when(chunk.getBlockId(0, 64, 0, 1)).thenReturn(BlockID.WATER);

        Structure captured = StructureCapture.capture(level,
                new StructureBox(new BlockVector3(0, 64, 0), new BlockVector3(1, 1, 1)), false);
        int secondary = captured.getBlockIndex(1, captured.index(0, 0, 0));
        assertEquals(BlockID.WATER << Block.DATA_BITS, captured.getLegacyId(secondary));
        assertTrue(captured.hasSecondaryLayer());

        BlockPalette palette = GlobalBlockPalette.getPaletteByProtocol(GameVersion.getFeatureVersion());
        Structure loaded = StructureIO.fromNbt(StructureIO.toNbt(captured), palette);
        int loadedSecondary = loaded.getBlockIndex(1, loaded.index(0, 0, 0));
        assertEquals(BlockID.WATER << Block.DATA_BITS, loaded.getLegacyId(loadedSecondary),
                "the waterlogged layer must survive the v1 file format");
    }

    @Test
    void structureVoidBecomesEmptyCell() {
        Level level = Mockito.mock(Level.class);
        BaseFullChunk chunk = Mockito.mock(BaseFullChunk.class);
        Mockito.when(level.getMinBlockY()).thenReturn(-64);
        Mockito.when(level.getMaxBlockY()).thenReturn(320);
        Mockito.when(level.getChunk(anyInt(), anyInt())).thenReturn(chunk);
        Mockito.when(level.getChunkBlockEntities(anyInt(), anyInt())).thenReturn(Map.of());
        Mockito.when(chunk.getBlockId(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(BlockID.AIR);
        Mockito.when(chunk.getBlockData(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(0);
        Mockito.when(chunk.getBlockId(0, 64, 0, 0)).thenReturn(BlockID.STRUCTURE_VOID);
        Mockito.when(chunk.getBlockId(1, 64, 0, 0)).thenReturn(BlockID.STONE);

        Structure structure = StructureCapture.capture(level,
                new StructureBox(new BlockVector3(0, 64, 0), new BlockVector3(2, 1, 1)), false);
        assertEquals(Structure.VOID, structure.getBlockIndex(0, structure.index(0, 0, 0)));
        int stone = structure.getBlockIndex(0, structure.index(1, 0, 0));
        assertNotEquals(Structure.VOID, stone);
        assertEquals(BlockID.STONE << Block.DATA_BITS, structure.getLegacyId(stone));
    }
}
