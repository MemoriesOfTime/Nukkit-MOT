package cn.nukkit.level;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockFence;
import cn.nukkit.block.BlockID;
import cn.nukkit.block.BlockWall;
import cn.nukkit.block.BlockWall.WallConnectionType;
import cn.nukkit.level.format.Chunk;
import cn.nukkit.level.format.ChunkSection;
import cn.nukkit.level.format.anvil.Anvil;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.generic.EmptyChunkSection;
import cn.nukkit.level.format.leveldb.structure.BlockStateSnapshot;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunkSection;
import cn.nukkit.level.format.leveldb.structure.StateBlockStorage;
import cn.nukkit.math.BlockFace;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

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
        // 旧世界内容：栅栏无连接位、门无 in_wall；门朝东（meta=3），门板轴为南北
        // pre-1.26.50 content: fence without connection bits, gate without in_wall; the gate
        // faces east (meta=3), so its panel axis is north-south
        chunk.setBlockAtLayer(FENCE_LOCAL_X, Y, FENCE_LOCAL_Z, 0, BlockID.FENCE, 0);
        chunk.setBlockAtLayer(FENCE_LOCAL_X, Y, FENCE_LOCAL_Z + 1, 0, BlockID.FENCE_GATE_OAK, 3);
        chunk.setNeedsLegacyConnectionFix(true);

        Assertions.assertEquals(BlockID.FENCE, chunk.getBlockId(FENCE_LOCAL_X, Y, FENCE_LOCAL_Z));
        Assertions.assertEquals(BlockID.FENCE_GATE_OAK, chunk.getBlockId(FENCE_LOCAL_X, Y, FENCE_LOCAL_Z + 1));

        Level level = Mockito.mock(Level.class, Mockito.CALLS_REAL_METHODS);
        // final 字段在 mock（未跑构造器）上直接可写 / final field is writable on a mock (constructor never ran)
        serverField().set(level, Server.getInstance());
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

        legacyConnectionFixMethod().invoke(level, CHUNK_X, CHUNK_Z, chunk);
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
     * 墙的状态依赖上方墙的连接与柱子位；迁移必须自顶向下，否则下层按尚未修正的上层状态计算，
     * 而 update=false 的写回不会触发邻居重算，区块随后被标记完成，错误状态就此落盘。
     * <p>
     * Wall state depends on the wall above, so migration has to run top-down. A bottom-up pass
     * computes lower walls against stale upper states, and the update=false write-back never
     * revisits them before the chunk is marked complete.
     */
    @Test
    void wallsMigrateTopDownAfterUpperWallLosesItsStaleConnection() throws Exception {
        final int x = 10;
        final int z = 10;
        final int lowerY = 100;
        final int upperY = 101;
        final int aboveY = 102;

        Map<Integer, int[]> blocks = new HashMap<>();
        // 旧规则残留：两层墙都是东西直连、非柱子；上层东侧还连着南瓜（新规则例外）
        // Pre-fix leftovers: both walls run east-west without a post; the upper one still
        // connects to a pumpkin, which the vanilla rules except.
        int staleStraight = wallMeta(WallConnectionType.TALL, WallConnectionType.TALL, false);
        int plainWall = wallMeta(WallConnectionType.NONE, WallConnectionType.NONE, true);
        BlockWall decoded = (BlockWall) Block.get(BlockID.STONE_WALL, staleStraight);
        Assertions.assertEquals(WallConnectionType.TALL, decoded.getConnectionType(BlockFace.EAST),
                "test fixture meta must round-trip wall states");

        blocks.put(localKey(x, lowerY, z), new int[]{BlockID.STONE_WALL, staleStraight});
        blocks.put(localKey(x - 1, lowerY, z), new int[]{BlockID.STONE_WALL, plainWall});
        blocks.put(localKey(x + 1, lowerY, z), new int[]{BlockID.STONE_WALL, plainWall});
        blocks.put(localKey(x, upperY, z), new int[]{BlockID.STONE_WALL, staleStraight});
        blocks.put(localKey(x - 1, upperY, z), new int[]{BlockID.STONE_WALL, plainWall});
        blocks.put(localKey(x + 1, upperY, z), new int[]{BlockID.PUMPKIN, 0});
        blocks.put(localKey(x, aboveY, z), new int[]{BlockID.STONE, 0});

        BaseFullChunk chunk = Mockito.mock(BaseFullChunk.class);
        Mockito.when(chunk.getX()).thenReturn(0);
        Mockito.when(chunk.getZ()).thenReturn(0);
        Mockito.when(chunk.isNeedsLegacyConnectionFix()).thenReturn(true);
        Mockito.when(chunk.getBlockId(anyInt(), anyInt(), anyInt()))
                .thenAnswer(inv -> blockAt(blocks, inv.getArgument(0), inv.getArgument(1), inv.getArgument(2))[0]);
        Mockito.when(chunk.getBlockState(anyInt(), anyInt(), anyInt(), anyInt()))
                .thenAnswer(inv -> blockAt(blocks, inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)).clone());

        Level level = Mockito.mock(Level.class, Mockito.CALLS_REAL_METHODS);
        Field serverField = Level.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(level, Server.getInstance());
        Mockito.doReturn(0).when(level).getMinBlockY();
        Mockito.doReturn(255).when(level).getMaxBlockY();
        Mockito.doReturn(chunk).when(level).getChunk(anyInt(), anyInt(), anyBoolean());
        Mockito.doReturn(populatedNeighbourMock()).when(level).getChunkIfLoaded(anyInt(), anyInt());
        Mockito.doAnswer(inv -> {
            Block written = inv.getArgument(2);
            blocks.put(localKey(written.getFloorX(), written.getFloorY(), written.getFloorZ()),
                    new int[]{written.getId(), written.getDamage()});
            return true;
        }).when(level).setBlock(Mockito.any(), Mockito.anyInt(), Mockito.any(Block.class),
                Mockito.anyBoolean(), Mockito.anyBoolean());

        legacyConnectionFixMethod().invoke(level, 0, 0, chunk);

        BlockWall upperAfter = wallAt(blocks, x, upperY, z);
        Assertions.assertEquals(WallConnectionType.NONE, upperAfter.getConnectionType(BlockFace.EAST),
                "the pumpkin must no longer connect");
        Assertions.assertTrue(upperAfter.isWallPost(), "a single remaining connection is not straight");

        BlockWall lowerAfter = wallAt(blocks, x, lowerY, z);
        Assertions.assertEquals(WallConnectionType.SHORT, lowerAfter.getConnectionType(BlockFace.EAST),
                "with the upper east arm gone the lower east arm must be short, not tall");
        Assertions.assertTrue(lowerAfter.isWallPost(),
                "the lower wall must inherit the corrected post state from above");
    }

    private static int localKey(int x, int y, int z) {
        return (y << 8) | (x << 4) | z;
    }

    private static int[] blockAt(Map<Integer, int[]> blocks, int x, int y, int z) {
        return blocks.getOrDefault(localKey(x, y, z), new int[]{BlockID.AIR, 0});
    }

    private static BlockWall wallAt(Map<Integer, int[]> blocks, int x, int y, int z) {
        int[] state = blockAt(blocks, x, y, z);
        Assertions.assertEquals(BlockID.STONE_WALL, state[0], "expected a wall at " + x + "," + y + "," + z);
        return (BlockWall) Block.get(state[0], state[1]);
    }

    private static int wallMeta(WallConnectionType east, WallConnectionType west, boolean post) {
        BlockWall wall = new BlockWall();
        wall.setConnection(BlockFace.EAST, east);
        wall.setConnection(BlockFace.WEST, west);
        wall.setWallPost(post);
        return wall.getDamage();
    }

    private static Field serverField() throws Exception {
        Field field = Level.class.getDeclaredField("server");
        field.setAccessible(true);
        return field;
    }

    private static Method legacyConnectionFixMethod() throws Exception {
        Method method = Level.class.getDeclaredMethod("fixLegacyBlockConnections", int.class, int.class, BaseFullChunk.class);
        method.setAccessible(true);
        return method;
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
            legacyConnectionFixMethod().invoke(level, 0, 0, chunk);
        }

        void verifyComplete() {
            verify(chunk).setNeedsLegacyConnectionFix(false);
            verify(chunk).setLegacyConnectionsFixed(true);
            verify(chunk).setChanged();
        }
    }
}
