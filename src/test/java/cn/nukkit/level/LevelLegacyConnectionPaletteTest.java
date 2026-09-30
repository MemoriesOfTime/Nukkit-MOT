package cn.nukkit.level;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.format.Chunk;
import cn.nukkit.level.format.ChunkSection;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.generic.EmptyChunkSection;
import cn.nukkit.level.format.leveldb.structure.BlockStateSnapshot;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunkSection;
import cn.nukkit.level.format.leveldb.structure.StateBlockStorage;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LevelLegacyConnectionPaletteTest {
    private static final IntSet TARGETS = new IntOpenHashSet(new int[]{BlockID.FENCE});

    @BeforeAll
    static void initServer() {
        MockServer.init();
    }

    @Test
    void palettesWithoutTargetsAvoidEveryBlockReadAndMarkComplete() throws Exception {
        ChunkSection stone = section(0, snapshot(BlockID.STONE));
        Fixture fixture = fixture(0, 31, 0, new ChunkSection[]{stone, EmptyChunkSection.bySectionY(1)});
        fixture.run();
        verify(fixture.chunk, never()).getBlockId(anyInt(), anyInt(), anyInt());
        fixture.verifyComplete();
    }

    @Test
    void candidatePaletteScansOnlyItsSection() throws Exception {
        Fixture fixture = fixture(0, 31, 0, new ChunkSection[]{
                section(0, snapshot(BlockID.STONE)), section(1, snapshot(BlockID.FENCE))});
        fixture.run();
        verify(fixture.chunk, times(4096)).getBlockId(anyInt(), intThat(y -> y >= 16 && y <= 31), anyInt());
        verify(fixture.chunk, never()).getBlockId(anyInt(), intThat(y -> y < 16), anyInt());
        fixture.verifyComplete();
    }

    @Test
    void negativeSectionsUseOffsetAndClipDimensionBounds() throws Exception {
        Fixture fixture = fixture(-63, -49, 4, new ChunkSection[]{section(-4, snapshot(BlockID.FENCE))});
        fixture.run();
        verify(fixture.chunk, times(15 * 256)).getBlockId(anyInt(), intThat(y -> y >= -63 && y <= -49), anyInt());
        verify(fixture.chunk, never()).getBlockId(anyInt(), intThat(y -> y < -63 || y > -49), anyInt());
        fixture.verifyComplete();
    }

    @Test
    void providersWithoutPaletteProbeRetainFullScan() throws Exception {
        ChunkSection section = mock(ChunkSection.class, CALLS_REAL_METHODS);
        assertTrue(section.mayContainBlockIds(TARGETS));
        Fixture fixture = fixture(0, 15, 0, new ChunkSection[]{section});
        fixture.run();
        verify(fixture.chunk, times(4096)).getBlockId(anyInt(), anyInt(), anyInt());
        fixture.verifyComplete();
    }

    @Test
    void unknownPaletteStateCannotCertifyAbsence() throws Exception {
        BlockStateSnapshot custom = BlockStateSnapshot.builder().legacyId(BlockID.STONE).custom(true).build();
        assertTrue(section(0, custom).mayContainBlockIds(TARGETS));
        assertTrue(section(0, snapshot(Block.INFO_UPDATE)).mayContainBlockIds(TARGETS));
        assertTrue(section(0, snapshot(-2)).mayContainBlockIds(TARGETS));
        assertTrue(section(0, (BlockStateSnapshot) null).mayContainBlockIds(TARGETS));
        assertTrue(section(0).mayContainBlockIds(TARGETS));
        Fixture fixture = fixture(0, 15, 0, new ChunkSection[]{section(0, custom)});
        fixture.run();
        verify(fixture.chunk, times(4096)).getBlockId(anyInt(), anyInt(), anyInt());
        fixture.verifyComplete();
    }

    @Test
    void extraLayerTargetsDoNotExpandMigrationScope() throws Exception {
        LevelDBChunkSection section = section(0, snapshot(BlockID.STONE));
        Field storagesField = LevelDBChunkSection.class.getDeclaredField("storages");
        storagesField.setAccessible(true);
        StateBlockStorage[] storages = (StateBlockStorage[]) storagesField.get(section);
        storages[1] = storage(snapshot(BlockID.FENCE));
        assertFalse(section.mayContainBlockIds(TARGETS));
        Fixture fixture = fixture(0, 15, 0, new ChunkSection[]{section});
        fixture.run();
        verify(fixture.chunk, never()).getBlockId(anyInt(), anyInt(), anyInt());
        fixture.verifyComplete();
    }

    @Test
    void unusedTargetPaletteEntryIsSafeFalsePositive() throws Exception {
        assertTrue(section(0, snapshot(BlockID.STONE), snapshot(BlockID.FENCE)).mayContainBlockIds(TARGETS));
        assertFalse(section(0, snapshot(BlockID.STONE), snapshot(BlockID.AIR)).mayContainBlockIds(TARGETS));
        assertFalse(EmptyChunkSection.bySectionY(0).mayContainBlockIds(TARGETS));
        assertTrue(EmptyChunkSection.bySectionY(0).mayContainBlockIds(new IntOpenHashSet(new int[]{BlockID.AIR})));
    }

    @Test
    void failedPaletteProbeKeepsMigrationPending() throws Exception {
        ChunkSection bad = mock(ChunkSection.class);
        when(bad.mayContainBlockIds(any())).thenThrow(new IllegalStateException("palette unavailable"));
        Fixture fixture = fixture(0, 31, 0, new ChunkSection[]{section(0, snapshot(BlockID.STONE)), bad});
        fixture.run();
        verify(fixture.chunk, never()).setNeedsLegacyConnectionFix(false);
        verify(fixture.chunk, never()).setLegacyConnectionsFixed(true);
        verify(fixture.chunk, never()).setChanged();
    }

    @Test
    void absentNeighbourDoesNotMarkPaletteOnlyPassComplete() throws Exception {
        Fixture fixture = fixture(0, 15, 0, new ChunkSection[]{section(0, snapshot(BlockID.STONE))});
        doReturn(null).when(fixture.level).getChunkIfLoaded(1, 0);
        fixture.run();
        verify(fixture.chunk, never()).setNeedsLegacyConnectionFix(false);
        verify(fixture.chunk, never()).setLegacyConnectionsFixed(true);
    }

    private static BlockStateSnapshot snapshot(int id) {
        return BlockStateSnapshot.builder().legacyId(id).legacyData(0).build();
    }

    private static StateBlockStorage storage(BlockStateSnapshot... states) throws Exception {
        StateBlockStorage storage = new StateBlockStorage();
        Field palette = StateBlockStorage.class.getDeclaredField("palette");
        palette.setAccessible(true);
        palette.set(storage, new ArrayList<>(Arrays.asList(states)));
        return storage;
    }

    private static LevelDBChunkSection section(int y, BlockStateSnapshot... states) throws Exception {
        LevelDBChunkSection section = new LevelDBChunkSection(y);
        Field storages = LevelDBChunkSection.class.getDeclaredField("storages");
        storages.setAccessible(true);
        storages.set(section, new StateBlockStorage[]{storage(states), null});
        return section;
    }

    private static Fixture fixture(int minY, int maxY, int offset, ChunkSection[] sections) throws Exception {
        BaseFullChunk chunk = mock(BaseFullChunk.class, withSettings().extraInterfaces(Chunk.class));
        Chunk sectionedChunk = (Chunk) chunk;
        when(sectionedChunk.getSections()).thenReturn(sections);
        when(sectionedChunk.getSectionOffset()).thenReturn(offset);
        when(chunk.isNeedsLegacyConnectionFix()).thenReturn(true);
        when(chunk.getBlockId(anyInt(), anyInt(), anyInt())).thenReturn(BlockID.STONE);
        Level level = mock(Level.class, CALLS_REAL_METHODS);
        Field server = Level.class.getDeclaredField("server");
        server.setAccessible(true);
        server.set(level, Server.getInstance());
        doReturn(minY).when(level).getMinBlockY();
        doReturn(maxY).when(level).getMaxBlockY();
        BaseFullChunk neighbour = mock(BaseFullChunk.class);
        when(neighbour.isPopulated()).thenReturn(true);
        doReturn(neighbour).when(level).getChunkIfLoaded(anyInt(), anyInt());
        return new Fixture(level, chunk);
    }

    private record Fixture(Level level, BaseFullChunk chunk) {
        void run() throws Exception {
            Method fix = Level.class.getDeclaredMethod("fixLegacyBlockConnections", int.class, int.class, BaseFullChunk.class);
            fix.setAccessible(true);
            fix.invoke(level, 0, 0, chunk);
        }

        void verifyComplete() {
            verify(chunk).setNeedsLegacyConnectionFix(false);
            verify(chunk).setLegacyConnectionsFixed(true);
            verify(chunk).setChanged();
        }
    }
}
