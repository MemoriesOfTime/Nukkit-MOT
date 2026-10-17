package cn.nukkit.utils;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockStone;
import cn.nukkit.block.custom.CustomBlockManager;
import cn.nukkit.block.custom.CustomBlockState;
import cn.nukkit.block.custom.container.BlockContainer;
import cn.nukkit.entity.Entity;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.SimpleAxisAlignedBB;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.cloudburstmc.nbt.NbtMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CollisionHelperTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @Nested
    class Air {
        private final Level level = mock(Level.class);
        private final Entity entity = mock(Entity.class);
        private final Map<Long, FullChunk> chunks = new HashMap<>();
        private final AxisAlignedBB bounds = new SimpleAxisAlignedBB(-0.5, 64, 0.1, 0.5, 65, 0.9);

        @BeforeEach
        void prepareWorld() {
            when(level.getMinBlockY()).thenReturn(-64);
            when(level.getMaxBlockY()).thenReturn(319);
            when(level.isYInRange(anyInt())).thenAnswer(invocation -> {
                int y = invocation.getArgument(0);
                return y >= -64 && y <= 319;
            });
            when(level.getChunkIfLoaded(anyInt(), anyInt())).thenAnswer(invocation ->
                    chunks.get(Level.chunkHash(invocation.getArgument(0), invocation.getArgument(1))));
            doCallRealMethod().when(level).getBlock(nullable(FullChunk.class), anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean());
            doCallRealMethod().when(level).getBlock(anyInt(), anyInt(), anyInt(), anyBoolean());
            when(entity.getLevel()).thenReturn(level);
        }

        @ParameterizedTest
        @ValueSource(booleans = {false, true})
        void customPredicateCanFindAirInLoadedAndUnloadedChunks(boolean targetFirst) {
            addChunk(0, 0, Block.AIR);

            List<Block> result = CollisionHelper.getCollisionBlocks(level, bounds, entity, targetFirst, true,
                    block -> block.getId() == Block.AIR);

            assertEquals(targetFirst ? 1 : 4, result.size());
            assertTrue(result.stream().allMatch(Block::isAir));
            // Traversal starts in the unloaded negative-X chunk, which also represents air.
            assertEquals(-1, result.get(0).getFloorX());
            verify(level, never()).getChunk(anyInt(), anyInt());
        }

        @Test
        void inclusiveBlockRangesDoNotVisitAnExtraFractionalUpperLayer() {
            for (double[] range : new double[][]{{0.1, 0.9}, {-0.9, -0.1}, {-1.9, -1.1},
                    {-0.1, 0.1}, {0.0, 1.0}, {-1.0, 0.0}, {-16.1, -15.9}}) {
                AxisAlignedBB box = new SimpleAxisAlignedBB(range[0], 64.1, 0.1, range[1], 64.9, 0.9);
                List<Block> result = CollisionHelper.getCollisionBlocks(level, box, entity, false, true,
                        block -> block.getId() == Block.AIR);
                List<Integer> expected = java.util.stream.IntStream.rangeClosed((int) Math.floor(range[0]),
                        (int) Math.floor(range[1])).boxed().toList();
                assertEquals(expected, result.stream().map(Block::getFloorX).toList(),
                        "inclusive cells for " + range[0] + ".." + range[1]);
                assertTrue(result.stream().allMatch(block -> block.getFloorY() == 64 && block.getFloorZ() == 0));
            }
            verify(level, never()).getChunk(anyInt(), anyInt());
        }

        @Test
        void entityQueriesUseInclusiveContainingChunksWithoutAnExtraFractionalChunk() {
            java.util.List<String> visited = new java.util.ArrayList<>();
            Level scanned = mock(Level.class, CALLS_REAL_METHODS);
            doAnswer(i -> {
                visited.add(i.getArgument(0) + ":" + i.getArgument(1));
                return null;
            }).when(scanned).getChunkIfLoaded(anyInt(), anyInt());
            for (int shift : new int[]{-16, 0, 16}) {
                for (double end : new double[]{12.1, 14.0}) {
                    AxisAlignedBB box = new SimpleAxisAlignedBB(2.1 + shift, 64, 2.1 + shift,
                            end + shift, 65, end + shift);
                    int min = shift / 16;
                    int max = (int) Math.floor((end + shift + 2) / 16);
                    java.util.List<String> expected = new java.util.ArrayList<>();
                    for (int x = min; x <= max; x++) {
                        for (int z = min; z <= max; z++) expected.add(x + ":" + z);
                    }
                    visited.clear();
                    assertTrue(CollisionHelper.getCollidingEntities(scanned, box, null).isEmpty());
                    assertEquals(expected, visited);
                    visited.clear();
                    assertEquals(0, scanned.getNearbyEntities(box, null, false, false).length);
                    assertEquals(expected, visited);
                }
            }
        }

        @Test
        void defaultQueriesSkipAirWithoutMaterializingBlocksOrLoadingMissingChunks() {
            addChunk(0, 0, Block.AIR);

            assertTrue(CollisionHelper.getCollisionBlocks(level, bounds, entity, false, true).isEmpty());
            assertFalse(CollisionHelper.hasCollisionBlocks(level, entity, bounds, true));
            assertTrue(CollisionHelper.getCollisionCubes(level, entity, bounds, false, false).isEmpty());
            CollisionHelper helper = new CollisionHelper(entity);
            assertEquals(0, helper.getBlocksInBoundingBox(bounds).length);
            assertFalse(helper.isInsideBlock(bounds, Block.LAVA));

            verify(level, never()).getBlock(nullable(FullChunk.class), anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean());
            verify(level, never()).getChunk(anyInt(), anyInt());
        }

        @Test
        void explicitAirInsideQueryStillMaterializesAirAndChecksItsCollisionBox() {
            addChunk(0, 0, Block.AIR);

            assertFalse(new CollisionHelper(entity).isInsideBlock(bounds, Block.AIR));

            verify(level, atLeastOnce()).getBlock(nullable(FullChunk.class), anyInt(), anyInt(), anyInt(), eq(0), eq(false));
        }

        @Test
        void solidBlocksAcrossChunkBoundaryKeepCoordinatesAndCollisionsWithWrongChunkHint() {
            addChunk(-1, 0, Block.STONE);
            addChunk(0, 0, Block.STONE);
            entity.chunk = addChunk(5, 5, Block.AIR);

            List<Block> result = CollisionHelper.getCollisionBlocks(level, bounds, entity, false, false);

            assertEquals(2, result.size());
            assertEquals(List.of(-1, 0), result.stream().map(Block::getFloorX).toList());
            assertTrue(result.stream().allMatch(block -> block.getId() == Block.STONE && block.getFloorY() == 64));
            assertTrue(CollisionHelper.hasCollisionBlocks(level, entity, bounds, true));
            assertEquals(2, CollisionHelper.getCollisionCubes(level, entity, bounds, false, false).size());
            assertTrue(new CollisionHelper(entity).isInsideBlock(bounds, Block.STONE));
            assertEquals(4, new CollisionHelper(entity).getBlocksInBoundingBox(bounds).length);
        }

        @Test
        void matchingEntityChunkHintWorksWithoutAnotherLoadedChunkLookup() {
            entity.chunk = addChunk(0, 0, Block.STONE);
            chunks.clear(); // The hint is still a valid chunk, even without a lookup entry.
            AxisAlignedBB localBounds = new SimpleAxisAlignedBB(0.1, -63.9, 0.1, 0.9, -63.1, 0.9);

            assertEquals(1, CollisionHelper.getCollisionBlocks(level, localBounds, entity, true, false).size());

            verify(level, never()).getChunkIfLoaded(anyInt(), anyInt());
        }

        @ParameterizedTest
        @ValueSource(booleans = {false, true})
        void destinationQueriesIgnoreAnEntityChunkFromAnotherWorldAtTheSameCoordinates(boolean destinationIsSolid) {
            int destinationId = destinationIsSolid ? Block.STONE : Block.AIR;
            int sourceId = destinationIsSolid ? Block.AIR : Block.STONE;
            addChunk(0, 0, destinationId);
            Level sourceLevel = mock(Level.class);
            FullChunk sourceChunk = mock(BaseFullChunk.class);
            when(sourceChunk.getBlockId(anyInt(), anyInt(), anyInt(), eq(0))).thenReturn(sourceId);
            when(sourceChunk.getBlockState(anyInt(), anyInt(), anyInt(), eq(0))).thenReturn(new int[]{sourceId, 0});
            when(entity.getLevel()).thenReturn(sourceLevel);
            entity.chunk = sourceChunk;
            AxisAlignedBB destinationBounds = new SimpleAxisAlignedBB(0.1, 64.1, 0.1, 0.9, 64.9, 0.9);
            int expectedCollisions = destinationIsSolid ? 1 : 0;

            assertAll(
                    () -> assertEquals(expectedCollisions,
                            CollisionHelper.getCollisionBlocks(level, destinationBounds, entity, true, false).size()),
                    () -> assertEquals(expectedCollisions,
                            CollisionHelper.getCollisionBlocks(level, destinationBounds, entity, false, false).size()),
                    () -> assertEquals(expectedCollisions,
                            CollisionHelper.getCollisionCubes(level, entity, destinationBounds, false, false).size()),
                    () -> assertEquals(destinationIsSolid,
                            CollisionHelper.hasCollisionBlocks(level, entity, destinationBounds, true)));
            verify(sourceChunk, never()).getBlockId(anyInt(), anyInt(), anyInt(), anyInt());
            verify(sourceChunk, never()).getBlockState(anyInt(), anyInt(), anyInt(), anyInt());
        }

        private FullChunk addChunk(int chunkX, int chunkZ, int id) {
            FullChunk chunk = mock(BaseFullChunk.class);
            when(chunk.getX()).thenReturn(chunkX);
            when(chunk.getZ()).thenReturn(chunkZ);
            when(chunk.getBlockId(anyInt(), anyInt(), anyInt(), eq(0))).thenReturn(id);
            when(chunk.getBlockState(anyInt(), anyInt(), anyInt(), eq(0))).thenReturn(new int[]{id, 0});
            chunks.put(Level.chunkHash(chunkX, chunkZ), chunk);
            return chunk;
        }
    }

    @Nested
    class BlockOwnership {

        @Test
        void vanillaMaterializationDoesNotAllocateAnotherClone() {
            Fixture f = new Fixture(Level.class);
            f.put(0, 0, 0, Block.STONE, 0);
            Block materialized = Block.get(Block.STONE, 0, f.level, 0, 0, 0, 0);
            try (MockedStatic<Block> blocks = mockStatic(Block.class, CALLS_REAL_METHODS)) {
                blocks.when(() -> Block.get(Block.STONE, 0, f.level, 0, 0, 0, 0)).thenReturn(materialized);
                assertSame(materialized, f.at(0, 0, 0));
            }
        }

        @Test
        void realVanillaReadsRemainIndependentAndKeepPositionAndLayer() {
            Fixture f = new Fixture(Level.class);
            f.put(2, 3, 4, Block.WATER, 0);
            Block first = f.at(2, 3, 4);
            Block second = f.at(2, 3, 4);
            assertNotSame(first, second);
            first.setDamage(5);
            first.x = 99;
            assertEquals(0, second.getDamage());
            assertEquals(2, second.x);
            assertEquals(3, second.y);
            assertEquals(4, second.z);
            assertSame(f.level, second.level);
            assertEquals(0, second.layer);
            assertEquals(0, f.at(2, 3, 4).getDamage());
        }

        @Test
        void extendedMetadataAndUnknownIdsAreNotPackedOrTruncated() {
            Fixture f = new Fixture(Level.class);
            f.put(1, 0, 0, Block.WATER, 8192);
            f.put(2, 0, 0, 9000, 9001);
            assertEquals(8192, f.at(1, 0, 0).getDamage());
            Block unknown = f.at(2, 0, 0);
            assertEquals(9000, unknown.getId());
            assertEquals(9001, unknown.getDamage());
        }

        @Test
        void customFactoryReturningCachedVanillaStillGetsSnapshot() throws Exception {
            assertCachedCustomSnapshot(false);
        }

        @Test
        void builtinAirProbeCannotHideCustomStateAtMaterialization() throws Exception {
            assertCachedCustomSnapshot(true);
        }

        @Test
        void nullCustomFactoryKeepsUnknownBlockFallbackAndMetadata() throws Exception {
            Fixture f = new Fixture(Level.class);
            int customId = CustomBlockManager.LOWEST_CUSTOM_BLOCK_ID + 992;
            Field field = CustomBlockManager.class.getDeclaredField("legacy2CustomState");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Int2ObjectMap<CustomBlockState> states = (Int2ObjectMap<CustomBlockState>) field.get(CustomBlockManager.get());
            int key = customId << Block.DATA_BITS;
            CustomBlockState previous = states.put(key,
                    new CustomBlockState("test:null_collision_factory", key, NbtMap.EMPTY, meta -> null));
            try {
                f.put(2, 0, 0, customId, 8192);
                Block block = f.at(2, 0, 0);
                assertEquals(customId, block.getId());
                assertEquals(8192, block.getDamage());
            } finally {
                if (previous == null) states.remove(key);
                else states.put(key, previous);
            }
        }

        @Test
        void stateChangedToAirAfterProbeRemainsExcluded() {
            Fixture f = new Fixture(Level.class);
            when(f.chunk.getBlockId(0, 0, 0, 0)).thenReturn(Block.STONE);
            assertEquals(0, f.helper.getBlocksInBoundingBox(point(0, 0, 0)).length);
            verify(f.chunk).getBlockState(0, 0, 0, 0);
        }

        private void assertCachedCustomSnapshot(boolean changedAfterProbe) throws Exception {
            Fixture f = new Fixture(Level.class);
            int customId = CustomBlockManager.LOWEST_CUSTOM_BLOCK_ID + 991;
            CachedStone cached = new CachedStone();
            Field field = CustomBlockManager.class.getDeclaredField("legacy2CustomState");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Int2ObjectMap<CustomBlockState> states = (Int2ObjectMap<CustomBlockState>) field.get(CustomBlockManager.get());
            int key = customId << Block.DATA_BITS;
            CustomBlockState previous = states.put(key,
                    new CustomBlockState("test:cached_collision_block", key, NbtMap.EMPTY, meta -> cached));
            try {
                f.put(2, 0, 0, customId, 0);
                f.put(3, 0, 0, customId, 0);
                if (changedAfterProbe) {
                    when(f.chunk.getBlockId(anyInt(), anyInt(), anyInt(), eq(0))).thenReturn(Block.STONE);
                }
                Block first = f.at(2, 0, 0);
                Block second = f.at(3, 0, 0);
                assertEquals(Block.STONE, first.getId(), "Returned class/id does not prove factory ownership");
                assertNotSame(cached, first);
                assertNotSame(first, second);
                assertEquals(2, first.x);
                assertEquals(3, second.x);
                assertEquals(3, cached.x);
            } finally {
                if (previous == null) states.remove(key);
                else states.put(key, previous);
            }
        }

        @Test
        void levelOverrideReturningCachedBlockStillGetsSnapshot() {
            Fixture f = new Fixture(CustomLevel.class);
            f.put(2, 0, 0, Block.STONE, 0);
            f.put(3, 0, 0, Block.STONE, 0);
            Block cached = Block.get(Block.STONE);
            doAnswer(invocation -> {
                    cached.x = (int) invocation.getArgument(1);
                    return cached;
                }).when(f.level).getBlock(eq(f.chunk), anyInt(), anyInt(), anyInt(), eq(0), eq(false));
            Block first = f.at(2, 0, 0);
            Block second = f.at(3, 0, 0);
            assertNotSame(cached, first);
            assertNotSame(first, second);
            assertEquals(2, first.x);
            assertEquals(3, second.x);
        }

        @Test
        void nullAndAirFromLevelOverrideStayExcluded() {
            Fixture f = new Fixture(CustomLevel.class);
            f.put(0, 0, 0, Block.STONE, 0);
            doReturn(null, Block.get(Block.AIR)).when(f.level).getBlock(f.chunk, 0, 0, 0, 0, false);
            assertEquals(0, f.helper.getBlocksInBoundingBox(point(0, 0, 0)).length);
            assertEquals(0, f.helper.getBlocksInBoundingBox(point(0, 0, 0)).length);
        }

        @Test
        void airUnloadedAndOutOfHeightDoNotMaterializeBlocks() {
            Fixture f = new Fixture(Level.class);
            assertEquals(0, f.helper.getBlocksInBoundingBox(point(0, 0, 0)).length);
            assertEquals(0, f.helper.getBlocksInBoundingBox(point(32, 0, 0)).length);
            assertEquals(0, f.helper.getBlocksInBoundingBox(point(0, 320, 0)).length);
            assertEquals(0, f.helper.getBlocksInBoundingBox(point(0, -65, 0)).length);
            verify(f.chunk, never()).getBlockState(anyInt(), anyInt(), anyInt(), anyInt());
            verify(f.level, never()).getBlock(any(), anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean());
            verify(f.level, never()).getChunk(anyInt(), anyInt());
        }

        @Test
        void matchingHintIsReusedAndWrongColumnLoadsOnlyExistingChunk() {
            Fixture f = new Fixture(Level.class);
            f.put(2, 0, 0, Block.STONE, 0);
            f.entity.chunk = f.chunk;
            assertEquals(Block.STONE, f.at(2, 0, 0).getId());
            verify(f.level, never()).getChunkIfLoaded(anyInt(), anyInt());
            BaseFullChunk wrongColumn = mock(BaseFullChunk.class);
            when(wrongColumn.getX()).thenReturn(1);
            f.entity.chunk = wrongColumn;
            assertEquals(Block.STONE, f.at(2, 0, 0).getId());
            verify(f.level).getChunkIfLoaded(0, 0);
            verify(wrongColumn, never()).getBlockState(anyInt(), anyInt(), anyInt(), anyInt());
            verify(f.level, never()).getChunk(anyInt(), anyInt());
        }

        @Test
        void dynamicAndSolidCollisionResultsKeepExistingBounds() {
            Fixture f = new Fixture(Level.class);
            when(f.entity.getBoundingBox()).thenReturn(new SimpleAxisAlignedBB(8.2, 8.1, 8.2, 8.8, 8.9, 8.8));
            f.put(8, 8, 8, Block.STONE, 0);
            f.put(9, 8, 8, Block.FIRE, 0);
            f.put(8, 8, 9, Block.STONE, 0);
            Block[] blocks = f.helper.getCollisionBlocks();
            assertEquals(2, blocks.length);
            assertEquals(Block.STONE, blocks[0].getId());
            assertEquals(8, blocks[0].x);
            assertEquals(Block.FIRE, blocks[1].getId());
            assertEquals(9, blocks[1].x);
            assertTrue(blocks[1].hasDynamicCollision());
        }

        private static SimpleAxisAlignedBB point(int x, int y, int z) {
            return new SimpleAxisAlignedBB(x, y, z, x, y, z);
        }

        private record Cell(int x, int y, int z) {}

        private static final class Fixture {
            final Level level;
            final BaseFullChunk chunk = mock(BaseFullChunk.class);
            final Entity entity = mock(Entity.class);
            final CollisionHelper helper = new CollisionHelper(entity);
            final Map<Cell, int[]> states = new HashMap<>();

            Fixture(Class<? extends Level> levelType) {
                level = mock(levelType);
                assertEquals(levelType, level.getClass(), "Ownership guard must exercise exact runtime class");
                when(level.getMinBlockY()).thenReturn(-64);
                when(level.getMaxBlockY()).thenReturn(319);
                when(level.getChunkIfLoaded(0, 0)).thenReturn(chunk);
                when(entity.getLevel()).thenReturn(level);
                when(chunk.getBlockId(anyInt(), anyInt(), anyInt(), eq(0)))
                        .thenAnswer(call -> state(call.getArgument(0), call.getArgument(1), call.getArgument(2))[0]);
                when(chunk.getBlockState(anyInt(), anyInt(), anyInt(), eq(0)))
                        .thenAnswer(call -> state(call.getArgument(0), call.getArgument(1), call.getArgument(2)).clone());
                when(level.getBlock(any(), anyInt(), anyInt(), anyInt(), eq(0), eq(false))).thenCallRealMethod();
                when(level.isYInRange(anyInt())).thenCallRealMethod();
            }

            void put(int x, int y, int z, int id, int meta) {
                states.put(new Cell(x, y, z), new int[]{id, meta});
            }

            int[] state(int x, int y, int z) {
                return states.getOrDefault(new Cell(x, y, z), new int[]{0, 0});
            }

            Block at(int x, int y, int z) {
                Block[] blocks = helper.getBlocksInBoundingBox(point(x, y, z));
                assertEquals(1, blocks.length);
                return blocks[0];
            }
        }

        private static class CustomLevel extends Level {
            CustomLevel() {
                super(null, "test", "test", null);
            }
        }

        private static class CachedStone extends BlockStone implements BlockContainer {
            @Override
            public String getIdentifier() {
                return "test:cached_collision_block";
            }

            @Override
            public int getNukkitId() {
                return Block.STONE;
            }
        }
    }

    /**
     * Regression test for main-thread freezes from malformed entity AABBs feeding {@link CollisionHelper}
     * runaway loops (cf. commit c446b28b).
     * Verifies pathological AABBs (oversized/NaN/Infinity) are rejected cheaply and emit a diagnostic warning.
     *
     * @author LT_Name
     */
    @Nested
    class Runaway {

        @BeforeEach
        public void setUp() {
            MockServer.init();
            // Reset throttle state so a prior test can't suppress this test's first warning.
            CollisionHelper.resetThrottleStateForTests();
        }

        @Test
        public void getCollisionBlocksStaticRejectsOversizedAABB() {
            // 2000^3 = 8e9 block positions — would freeze the main thread before the cap.
            AxisAlignedBB huge = new SimpleAxisAlignedBB(0, 0, 0, 2000, 2000, 2000);
            Level level = Mockito.mock(Level.class);
            Mockito.when(level.getMinBlockY()).thenReturn(-64);
            Mockito.when(level.getMaxBlockY()).thenReturn(319);

            List<Block> result = CollisionHelper.getCollisionBlocks(level, huge);

            assertTrue(result.isEmpty(), "Oversized AABB must short-circuit to empty list");
        }

        @Test
        public void getCollisionBlocksStaticRejectsNaNBounds() {
            AxisAlignedBB nan = new SimpleAxisAlignedBB(Double.NaN, 0, 0, 1, 1, 1);
            Level level = Mockito.mock(Level.class);

            List<Block> result = CollisionHelper.getCollisionBlocks(level, nan);

            assertTrue(result.isEmpty(), "Non-finite AABB must be rejected");
        }

        @Test
        public void getCollisionBlocksStaticRejectsInfiniteBounds() {
            AxisAlignedBB inf = new SimpleAxisAlignedBB(
                    Double.NEGATIVE_INFINITY, 0, 0,
                    Double.POSITIVE_INFINITY, 1, 1);
            Level level = Mockito.mock(Level.class);

            List<Block> result = CollisionHelper.getCollisionBlocks(level, inf);

            assertTrue(result.isEmpty(), "Infinite AABB must be rejected");
        }

        @Test
        public void getCollisionCubesRejectsOversizedAABB() {
            AxisAlignedBB huge = new SimpleAxisAlignedBB(0, 0, 0, 2000, 2000, 2000);
            Level level = Mockito.mock(Level.class);
            Mockito.when(level.getMinBlockY()).thenReturn(-64);
            Mockito.when(level.getMaxBlockY()).thenReturn(319);
            Entity entity = Mockito.mock(Entity.class);

            List<AxisAlignedBB> result = CollisionHelper.getCollisionCubes(level, entity, huge, false, false);

            assertTrue(result.isEmpty(), "Oversized AABB must short-circuit to empty list");
        }

        @Test
        public void getCollidingEntitiesRejectsOversizedAABB() {
            // Coordinates span ~560 chunks per axis — would iterate ~310k chunks.
            AxisAlignedBB huge = new SimpleAxisAlignedBB(0, 0, 0, 9000, 1, 9000);
            Level level = Mockito.mock(Level.class);

            List<Entity> result = CollisionHelper.getCollidingEntities(level, huge);

            assertTrue(result.isEmpty(), "Oversized AABB must short-circuit to empty list");
        }

        @Test
        public void hasCollisionBlocksRejectsOversizedAABB() {
            AxisAlignedBB huge = new SimpleAxisAlignedBB(0, 0, 0, 2000, 2000, 2000);
            Level level = Mockito.mock(Level.class);
            Mockito.when(level.getMinBlockY()).thenReturn(-64);
            Mockito.when(level.getMaxBlockY()).thenReturn(319);

            boolean result = CollisionHelper.hasCollisionBlocks(level, null, huge, false);

            assertTrue(!result, "Oversized AABB must report no collisions");
        }

        @Test
        public void runawayAABBEmitsDiagnosticWarning() {
            Server mockServer = MockServer.get();
            MainLogger logger = mockServer.getLogger();

            AxisAlignedBB huge = new SimpleAxisAlignedBB(0, 0, 0, 5000, 5000, 5000);
            Level level = Mockito.mock(Level.class);
            Mockito.when(level.getMinBlockY()).thenReturn(-64);
            Mockito.when(level.getMaxBlockY()).thenReturn(319);

            CollisionHelper.getCollisionBlocks(level, huge);

            // A warning should be logged exactly once per call (no prior throttle entry for this BB).
            Mockito.verify(logger, Mockito.atLeastOnce()).warning(Mockito.contains("Runaway collision AABB"));
        }

        @Test
        public void runawayAABBSkipLoggingForNormalSizedAABB() {
            Server mockServer = MockServer.get();
            MainLogger logger = mockServer.getLogger();
            Mockito.clearInvocations(logger);

            AxisAlignedBB normal = new SimpleAxisAlignedBB(0, 0, 0, 2, 2, 2);
            Level level = Mockito.mock(Level.class);
            Mockito.when(level.getMinBlockY()).thenReturn(-64);
            Mockito.when(level.getMaxBlockY()).thenReturn(319);

            CollisionHelper.getCollisionBlocks(level, normal);

            Mockito.verify(logger, Mockito.never()).warning(Mockito.contains("Runaway collision AABB"));
        }

        /** Verifies the per-entity throttle: first runaway call logs, the second within the window is suppressed (instance API only). */
        @Test
        public void runawayAABBThrottlesPerEntity() {
            AxisAlignedBB huge = new SimpleAxisAlignedBB(0, 0, 0, 2000, 2000, 2000);
            Entity entity = Mockito.mock(Entity.class);
            Level level = Mockito.mock(Level.class);
            Mockito.when(level.getMinBlockY()).thenReturn(-64);
            Mockito.when(level.getMaxBlockY()).thenReturn(319);
            Mockito.when(entity.getLevel()).thenReturn(level);
            Mockito.when(entity.isClosed()).thenReturn(false);
            Mockito.when(entity.getBoundingBox()).thenReturn(huge);
            Mockito.when(entity.getId()).thenReturn(99L);

            MainLogger logger = MockServer.get().getLogger();
            Mockito.clearInvocations(logger);

            CollisionHelper helper = new CollisionHelper(entity);
            helper.getBlocksInBoundingBox(huge); // first call — should log
            helper.getBlocksInBoundingBox(huge); // second call within window — should be throttled

            // Exactly one warning across the two calls.
            Mockito.verify(logger, Mockito.times(1)).warning(Mockito.contains("Runaway collision AABB"));
        }

        @Test
        public void getCollisionBlocksEntityHelperRejectsOversizedAABB() {
            AxisAlignedBB huge = new SimpleAxisAlignedBB(0, 0, 0, 2000, 2000, 2000);
            Entity entity = Mockito.mock(Entity.class);
            Level level = Mockito.mock(Level.class);
            Mockito.when(level.getMinBlockY()).thenReturn(-64);
            Mockito.when(level.getMaxBlockY()).thenReturn(319);
            Mockito.when(entity.getLevel()).thenReturn(level);
            Mockito.when(entity.isClosed()).thenReturn(false);
            Mockito.when(entity.getBoundingBox()).thenReturn(huge);
            Mockito.when(entity.getId()).thenReturn(42L);

            CollisionHelper helper = new CollisionHelper(entity);

            Block[] result = helper.getBlocksInBoundingBox(huge);

            assertEquals(0, result.length, "Oversized AABB must short-circuit to empty array");
        }

        @Test
        public void isInsideBlockUsesCollisionBoxForLava() {
            AxisAlignedBB bb = new SimpleAxisAlignedBB(0.3, 0, 0.3, 0.7, 1.8, 0.7);
            Level level = Mockito.mock(Level.class);
            Entity entity = Mockito.mock(Entity.class);
            FullChunk chunk = Mockito.mock(FullChunk.class);
            entity.chunk = chunk;
            Mockito.when(chunk.getBlockId(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(), Mockito.eq(0)))
                    .thenReturn(Block.LAVA);
            Block lava = Block.get(Block.LAVA);
            lava.setLevel(level);
            lava.setComponents(0, 0, 0);

            Mockito.when(level.getMinBlockY()).thenReturn(-64);
            Mockito.when(level.getMaxBlockY()).thenReturn(319);
            Mockito.when(level.getBlock(Mockito.any(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(),
                    Mockito.anyInt(), Mockito.anyBoolean())).thenReturn(lava);
            Mockito.when(entity.getLevel()).thenReturn(level);
            Mockito.when(entity.isClosed()).thenReturn(false);

            CollisionHelper helper = new CollisionHelper(entity);

            assertTrue(helper.isInsideBlock(bb, Block.LAVA),
                    "Lava has no normal bounding box, so inside checks must use its collision box");
        }

        /** A bogus motion on a normal BB must NOT trip the cap: motion expansion is clamped per-axis, so the real collision volume is still scanned correctly. */
        @Test
        public void getCollisionBlocksWithBogusMotionStillScansBaseBoundingBox() {
            // Normal 1x1x1 box, but a runaway motion that would have grown the sweep to ~1e18 blocks.
            AxisAlignedBB bb = new SimpleAxisAlignedBB(0, 0, 0, 1, 1, 1);
            Entity entity = Mockito.mock(Entity.class);
            Level level = Mockito.mock(Level.class);
            Mockito.when(level.getMinBlockY()).thenReturn(-64);
            Mockito.when(level.getMaxBlockY()).thenReturn(319);
            Mockito.when(level.getBlock(Mockito.any(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(),
                    Mockito.anyInt(), Mockito.anyBoolean())).thenReturn(Block.get(0, 0));
            Mockito.when(entity.getLevel()).thenReturn(level);
            Mockito.when(entity.isClosed()).thenReturn(false);
            Mockito.when(entity.getBoundingBox()).thenReturn(bb);
            entity.motionX = 1.0e9;
            entity.motionY = 1.0e9;
            entity.motionZ = 1.0e9;

            CollisionHelper helper = new CollisionHelper(entity);

            // Must complete without tripping the cap (no freeze) and return empty (all air).
            Block[] result = helper.getCollisionBlocks();

            assertEquals(0, result.length);
        }
    }
}
