package cn.nukkit.level;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockFence;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.format.Chunk;
import cn.nukkit.level.format.ChunkSection;
import cn.nukkit.level.format.anvil.Anvil;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.generic.EmptyChunkSection;
import cn.nukkit.level.format.leveldb.structure.BlockStateSnapshot;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunkSection;
import cn.nukkit.level.format.leveldb.structure.StateBlockStorage;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * 旧世界连接位迁移的区块扫描：fixLegacyBlockConnections 必须真的扫到非 (0,0) 区块里的目标方块。
 * 曾把 chunk 局部坐标传给期望世界坐标的 getBlockIdAt，守卫恒假，除 (0,0) 外所有区块空转
 * 扫描后照常清标志——旧世界栅栏/玻璃板/楼梯永不重算且不再重试。
 * <p>
 * Chunk scan of the legacy connection migration: fixLegacyBlockConnections must actually reach target
 * blocks in chunks other than (0,0). Chunk-local coords were once passed to getBlockIdAt (world coords),
 * so its guard failed and every chunk silently scanned nothing before clearing the flag — pre-1.26.50
 * fences/panes/stairs were never recomputed and never retried.
 */
class LevelLegacyConnectionTest {

    private static final int CHUNK_X = 3;
    private static final int CHUNK_Z = 7;
    private static final int FENCE_LOCAL_X = 5;
    private static final int FENCE_LOCAL_Z = 9;
    private static final int Y = 65;

    @BeforeAll
    static void initServer() {
        MockServer.init();
    }

    @Test
    void fenceNextToFenceGateInNonZeroChunkRegainsConnection() throws Exception {
        cn.nukkit.level.format.anvil.Chunk chunk = new cn.nukkit.level.format.anvil.Chunk(Anvil.class);
        chunk.setPosition(CHUNK_X, CHUNK_Z);
        // 旧世界内容：栅栏与栅栏门均无连接位（meta=0）
        // pre-1.26.50 content: neither fence nor gate carries connection bits (meta=0)
        chunk.setBlockAtLayer(FENCE_LOCAL_X, Y, FENCE_LOCAL_Z, 0, BlockID.FENCE, 0);
        chunk.setBlockAtLayer(FENCE_LOCAL_X, Y, FENCE_LOCAL_Z + 1, 0, BlockID.FENCE_GATE_OAK, 0);
        chunk.setNeedsLegacyConnectionFix(true);

        Assertions.assertEquals(BlockID.FENCE, chunk.getBlockId(FENCE_LOCAL_X, Y, FENCE_LOCAL_Z));
        Assertions.assertEquals(BlockID.FENCE_GATE_OAK, chunk.getBlockId(FENCE_LOCAL_X, Y, FENCE_LOCAL_Z + 1));

        Level level = Mockito.mock(Level.class, Mockito.CALLS_REAL_METHODS);
        Field serverField = Level.class.getDeclaredField("server");
        serverField.setAccessible(true);
        // final 字段在 mock（未跑构造器）上直接可写 / final field is writable on a mock (constructor never ran)
        serverField.set(level, Server.getInstance());
        List<Throwable> scanErrors = new ArrayList<>();
        cn.nukkit.utils.MainLogger logger = Server.getInstance().getLogger();
        Mockito.doAnswer(inv -> {
            if (inv.getArguments().length > 1 && inv.getArgument(1) instanceof Throwable t) {
                scanErrors.add(t);
            }
            return null;
        }).when(logger).error(Mockito.anyString(), Mockito.any(Throwable.class));

        // Anvil 区块段覆盖 Y 0..255，维度范围须与之配对 / anvil sections cover Y 0..255
        Mockito.doReturn(0).when(level).getMinBlockY();
        Mockito.doReturn(255).when(level).getMaxBlockY();
        Mockito.doAnswer(inv -> inv.getArgument(0).equals(CHUNK_X) && inv.getArgument(1).equals(CHUNK_Z)
                        ? chunk : populatedNeighbourMock())
                .when(level).getChunk(anyInt(), anyInt(), anyBoolean());
        Mockito.doAnswer(inv -> inv.getArgument(0).equals(CHUNK_X) && inv.getArgument(1).equals(CHUNK_Z)
                        ? chunk : populatedNeighbourMock())
                .when(level).getChunkIfLoaded(anyInt(), anyInt());

        List<Block> written = new ArrayList<>();
        Mockito.doAnswer(inv -> {
            Block reconnected = inv.getArgument(2);
            written.add(reconnected);
            chunk.setBlockAtLayer(reconnected.getFloorX() & 0xF, reconnected.getFloorY(),
                    reconnected.getFloorZ() & 0xF, 0, reconnected.getId(), reconnected.getDamage());
            return true;
        }).when(level).setBlock(Mockito.any(), Mockito.anyInt(), Mockito.any(Block.class),
                Mockito.anyBoolean(), Mockito.anyBoolean());

        // canConnect 必须认得栅栏门：手动重算应得到指向门的南向位
        // canConnect must recognize fence gates: a manual recompute derives the south bit
        Block raw = level.getBlock((CHUNK_X << 4) + FENCE_LOCAL_X, Y, (CHUNK_Z << 4) + FENCE_LOCAL_Z, 0);
        Assertions.assertInstanceOf(BlockFence.class, raw);
        Assertions.assertTrue(((BlockFence) raw).updateConnections());
        Assertions.assertEquals(BlockFence.FLAG_CONNECTION_SOUTH, raw.getDamage() & BlockFence.CONNECTION_FLAGS);

        Method fix = Level.class.getDeclaredMethod("fixLegacyBlockConnections", int.class, int.class, BaseFullChunk.class);
        fix.setAccessible(true);
        fix.invoke(level, CHUNK_X, CHUNK_Z, chunk);

        Assertions.assertTrue(scanErrors.isEmpty(), "scan must not throw: " + (scanErrors.isEmpty() ? "" : scanErrors.get(0)));
        Assertions.assertEquals(1, written.size(), "the fence must be written back once");
        Block fence = written.get(0);
        Assertions.assertInstanceOf(BlockFence.class, fence);
        Assertions.assertEquals(BlockFence.FLAG_CONNECTION_SOUTH, fence.getDamage() & BlockFence.CONNECTION_FLAGS,
                "only the south arm (towards the gate) may be set");
        Assertions.assertEquals(BlockFence.FLAG_CONNECTION_SOUTH,
                chunk.getBlockData(FENCE_LOCAL_X, Y, FENCE_LOCAL_Z) & BlockFence.CONNECTION_FLAGS,
                "write-back must land in the chunk");
        Assertions.assertFalse(chunk.isNeedsLegacyConnectionFix(), "flag clears after a successful scan");
    }

    /**
     * 四邻门控要求邻居已 populate：mock 默认 isPopulated()=false 会卡门控 / the four-neighbour
     * gate requires populated neighbours; a bare mock's isPopulated()=false would stall it.
     */
    private static BaseFullChunk populatedNeighbourMock() {
        BaseFullChunk neighbour = Mockito.mock(BaseFullChunk.class);
        Mockito.doReturn(true).when(neighbour).isPopulated();
        return neighbour;
    }

    private static final IntSet TARGETS = new IntOpenHashSet(new int[]{BlockID.FENCE});

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
