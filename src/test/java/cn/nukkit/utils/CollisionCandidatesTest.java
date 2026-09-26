package cn.nukkit.utils;

import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.block.custom.CustomBlockManager;
import cn.nukkit.entity.Entity;
import cn.nukkit.level.DimensionData;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.ChunkSection;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import cn.nukkit.level.format.leveldb.structure.BlockStateSnapshot;
import cn.nukkit.level.format.leveldb.structure.ChunkState;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunkSection;
import cn.nukkit.level.format.leveldb.structure.StateBlockStorage;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The collision candidate scan must give getCollisionBlocks exactly what the full scan gave it:
 * the same blocks, in the same order, materialised the same way.
 */
class CollisionCandidatesTest {

    /** Everything a mob can stand in, next to or under - including all shapes that leave their cell. */
    private static final int[] PALETTE = {
            BlockID.STONE, BlockID.GRASS, BlockID.DIRT, BlockID.TALL_GRASS, BlockID.WATER, BlockID.STILL_WATER,
            BlockID.LAVA, BlockID.STILL_LAVA, BlockID.FIRE, BlockID.SOUL_FIRE, BlockID.NETHER_PORTAL,
            BlockID.FENCE, BlockID.COBBLESTONE_WALL, BlockID.FENCE_GATE_OAK, BlockID.COBWEB, BlockID.CACTUS,
            BlockID.SWEET_BERRY_BUSH, BlockID.STONE_SLAB, BlockID.OAK_WOOD_STAIRS, BlockID.CARPET,
            BlockID.SNOW_LAYER, BlockID.HOPPER_BLOCK, BlockID.GLASS_PANE, BlockID.IRON_BARS, BlockID.LADDER,
            BlockID.VINE, BlockID.RAIL, BlockID.STONE_PRESSURE_PLATE, BlockID.TRIPWIRE, BlockID.SOUL_SAND,
            BlockID.FARMLAND, BlockID.MAGMA, BlockID.HONEY_BLOCK, BlockID.POWDER_SNOW, BlockID.SCAFFOLDING,
            BlockID.END_PORTAL, BlockID.TORCH,
            // Custom and unregistered ids: nothing is known about their shape, both scans must keep them.
            CustomBlockManager.LOWEST_CUSTOM_BLOCK_ID + 991, 2040
    };

    private static World world;

    @BeforeAll
    static void init() {
        MockServer.init();
        world = new World(new Random(20260925L));
    }

    @Test
    void sameBlocksInSameOrderAsTheFullScan() {
        Random random = new Random(7);
        int compared = 0;
        int nonEmpty = 0;
        for (int round = 0; round < 20_000; round++) {
            double width = 0.2 + random.nextDouble() * 1.6;
            double height = 0.2 + random.nextDouble() * 2.2;
            double x = -12 + random.nextDouble() * 24;
            double z = -12 + random.nextDouble() * 24;
            double y = 30 + random.nextDouble() * 40;
            if (random.nextInt(4) == 0) {
                // Standing exactly on a block top or on a fence post height.
                y = Math.floor(y) + (random.nextBoolean() ? 0 : 0.5);
            }
            if (random.nextInt(6) == 0) {
                x = Math.floor(x) + width / 2;
            }
            AxisAlignedBB box = new SimpleAxisAlignedBB(x - width / 2, y, z - width / 2, x + width / 2, y + height, z + width / 2);
            double expandX = Math.min(64, Math.max(0.5, random.nextDouble() * (random.nextInt(8) == 0 ? 3 : 0.4) + 0.3));
            double expandY = Math.min(64, Math.max(0.5, random.nextDouble() * (random.nextInt(8) == 0 ? 3 : 0.4) + 0.3));
            double expandZ = Math.min(64, Math.max(0.5, random.nextDouble() * (random.nextInt(8) == 0 ? 3 : 0.4) + 0.3));
            AxisAlignedBB scan = box.grow(expandX, expandY, expandZ);

            CollisionHelper helper = world.helperAt(x, z);
            Block[] expected = CollisionHelper.filterCollisionBlocks(helper.getBlocksInBoundingBox(scan), box, expandX, expandY, expandZ);
            Block[] actual = CollisionHelper.filterCollisionBlocks(helper.getCollisionCandidates(scan, box), box, expandX, expandY, expandZ);
            assertSameBlocks(expected, actual, box, scan);
            compared++;
            if (expected.length > 0) {
                nonEmpty++;
            }
        }
        assertEquals(20_000, compared);
        assertTrue(nonEmpty > 5_000, "the world must produce real collisions, got " + nonEmpty);
    }

    @Test
    void candidatesSkipGroundOutsideTheEntityCells() {
        // A 1.4 x 1.6 mob standing on stone in open air: the scan covers 4 x 5 x 4 cells, only fire,
        // portals, fences below the feet and unknown ids may come from outside the three cell layers it
        // overlaps, so the stone floor must not be materialised at all.
        World flat = World.flat(BlockID.STONE, 63);
        CollisionHelper helper = flat.helperAt(0.5, 0.5);
        AxisAlignedBB box = new SimpleAxisAlignedBB(-0.2, 64, -0.2, 1.2, 65.6, 1.2);
        AxisAlignedBB scan = box.grow(0.5, 0.5, 0.5);
        assertTrue(helper.getBlocksInBoundingBox(scan).length > 0);
        assertEquals(0, helper.getCollisionCandidates(scan, box).length);
    }

    @Test
    void aRescanForgetsTheChunksOfTheScanBefore() throws ReflectiveOperationException {
        // The cache lives as long as the entity: a slot the new scan does not overwrite must not keep
        // the chunk and section of an older scan reachable (an unloaded or reloaded chunk).
        CollisionHelper.ScanCache cache = new CollisionHelper.ScanCache();
        Object first = new Object();
        Object second = new Object();
        Object third = new Object();
        cache.begin(null);
        cache.stamp(0, 0, 4, first, first, 1);
        cache.stamp(1, 0, 4, second, second, 1);
        cache.begin(null);
        cache.stamp(0, 0, 4, third, third, 2);

        java.lang.reflect.Field chunks = CollisionHelper.ScanCache.class.getDeclaredField("stampChunk");
        java.lang.reflect.Field sections = CollisionHelper.ScanCache.class.getDeclaredField("stampSection");
        chunks.setAccessible(true);
        sections.setAccessible(true);
        Object[] chunkSlots = (Object[]) chunks.get(cache);
        Object[] sectionSlots = (Object[]) sections.get(cache);
        assertSame(third, chunkSlots[0]);
        assertSame(third, sectionSlots[0]);
        assertNull(chunkSlots[1]);
        assertNull(sectionSlots[1]);
    }

    @Test
    void restingEntityDoesNotReadItsSectionAgain() {
        World flat = World.flat(BlockID.STONE, 63);
        LevelDBChunk chunk = flat.chunk(0, 0);
        LevelDBChunkSection section = spy((LevelDBChunkSection) chunk.getSection(3));
        World.replaceSection(chunk, 3, section);
        CollisionHelper helper = flat.helperAt(5.5, 5.5);
        AxisAlignedBB box = new SimpleAxisAlignedBB(4.8, 64, 4.8, 6.2, 65.6, 6.2);
        AxisAlignedBB scan = box.grow(0.5, 0.5, 0.5);

        helper.getCollisionCandidates(scan, box);
        helper.getCollisionCandidates(scan, box);
        helper.getCollisionCandidates(scan, box);
        verify(section, times(1)).getBlockStatePairs(anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(),
                any(), anyInt(), anyInt(), anyInt());

        // A moved entity scans again.
        AxisAlignedBB moved = box.getOffsetBoundingBox(0.1, 0, 0);
        helper.getCollisionCandidates(moved.grow(0.5, 0.5, 0.5), moved);
        verify(section, times(2)).getBlockStatePairs(anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(),
                any(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void restingScanFollowsEveryChangeAroundTheEntity() {
        // One cached helper per resting entity, compared after random edits with a fresh full scan.
        Random random = new Random(11);
        World world = new World(new Random(99));
        int[] edits = {BlockID.AIR, BlockID.STONE, BlockID.FIRE, BlockID.FENCE, BlockID.TALL_GRASS, BlockID.WATER,
                BlockID.NETHER_PORTAL, BlockID.COBBLESTONE_WALL, CustomBlockManager.LOWEST_CUSTOM_BLOCK_ID + 991};
        int checked = 0;
        for (int entity = 0; entity < 300; entity++) {
            double x = -10 + random.nextDouble() * 20;
            double z = -10 + random.nextDouble() * 20;
            double y = 34 + random.nextDouble() * 30;
            double width = 0.3 + random.nextDouble() * 1.3;
            AxisAlignedBB box = new SimpleAxisAlignedBB(x - width / 2, y, z - width / 2, x + width / 2, y + 1.6, z + width / 2);
            AxisAlignedBB scan = box.grow(0.5, 0.5, 0.5);
            CollisionHelper cached = world.helperAt(x, z);
            for (int tick = 0; tick < 12; tick++) {
                int change = random.nextInt(6);
                int bx = (int) Math.floor(x) + random.nextInt(5) - 2;
                int by = (int) Math.floor(y) + random.nextInt(5) - 2;
                int bz = (int) Math.floor(z) + random.nextInt(5) - 2;
                if (change == 0) {
                    world.set(bx, by, bz, edits[random.nextInt(edits.length)]);
                } else if (change == 1) {
                    world.replaceSectionAt(bx, by, bz, random);
                } else if (change == 2) {
                    world.reloadChunkAt(bx, bz);
                    // A live entity always points at its loaded chunk: unloading closes it.
                    cached.entity().chunk = world.chunk((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
                }
                // The full scan of the same entity, which never uses the cache.
                Block[] expected = CollisionHelper.filterCollisionBlocks(
                        cached.getBlocksInBoundingBox(scan), box, 0.5, 0.5, 0.5);
                Block[] actual = CollisionHelper.filterCollisionBlocks(
                        cached.getCollisionCandidates(scan, box), box, 0.5, 0.5, 0.5);
                assertSameBlocks(expected, actual, box, scan);
                checked++;
            }
        }
        assertEquals(3600, checked);
    }

    @Test
    void everyNonOverhangShapeStaysInsideItsCell() {
        // The candidate scan relies on it: a block that is neither dynamic nor a fence, gate or wall can
        // only touch a box that overlaps its own cell; fences, gates and walls reach 0.5 above it.
        byte[] flags = CollisionHelper.shapeFlags();
        assertNotNull(flags);
        int checked = 0;
        List<String> leaks = new ArrayList<>();
        for (int neighbour : new int[]{BlockID.AIR, BlockID.STONE}) {
            Level level = World.uniform(neighbour);
            for (int id = 0; id < flags.length; id++) {
                if ((flags[id] & (CollisionHelper.SHAPE_UNKNOWN | CollisionHelper.SHAPE_DYNAMIC)) != 0) {
                    continue;
                }
                boolean overhang = (flags[id] & CollisionHelper.SHAPE_OVERHANG) != 0;
                for (int meta = 0; meta < 16; meta++) {
                    Block block;
                    try {
                        block = Block.get(id, meta, level, 8, 64, 8, 0);
                    } catch (Throwable t) {
                        continue;
                    }
                    if (block instanceof cn.nukkit.block.BlockUnknown) {
                        continue;
                    }
                    double top = overhang ? 65.5 : 65.0;
                    AxisAlignedBB[] outside = {
                            new SimpleAxisAlignedBB(7, 62, 7, 10, 64, 10),      // below the cell
                            new SimpleAxisAlignedBB(7, top, 7, 10, 67, 10),     // above the cell (and the post)
                            new SimpleAxisAlignedBB(6, 62, 7, 8, 67, 10),       // west
                            new SimpleAxisAlignedBB(9, 62, 7, 11, 67, 10),      // east
                            new SimpleAxisAlignedBB(7, 62, 6, 10, 67, 8),       // north
                            new SimpleAxisAlignedBB(7, 62, 9, 10, 67, 11),      // south
                    };
                    for (AxisAlignedBB probe : outside) {
                        boolean collides;
                        try {
                            collides = block.collidesWithBB(probe, true);
                        } catch (Throwable t) {
                            continue;
                        }
                        if (collides) {
                            leaks.add(block.getClass().getSimpleName() + " id=" + id + " meta=" + meta + " probe=" + probe);
                        }
                    }
                    checked++;
                }
            }
        }
        assertTrue(checked > 1000, "checked " + checked);
        assertEquals(List.of(), leaks);
    }

    @Test
    void onlyFireAndPortalsCollideThroughTheTrajectoryBox() {
        byte[] flags = CollisionHelper.shapeFlags();
        assertNotNull(flags);
        assertNotEquals(0, flags[BlockID.FIRE] & CollisionHelper.SHAPE_DYNAMIC);
        assertNotEquals(0, flags[BlockID.SOUL_FIRE] & CollisionHelper.SHAPE_DYNAMIC);
        assertNotEquals(0, flags[BlockID.NETHER_PORTAL] & CollisionHelper.SHAPE_DYNAMIC);
        assertNotEquals(0, flags[BlockID.FENCE] & CollisionHelper.SHAPE_OVERHANG);
        assertNotEquals(0, flags[BlockID.COBBLESTONE_WALL] & CollisionHelper.SHAPE_OVERHANG);
        assertNotEquals(0, flags[BlockID.FENCE_GATE_OAK] & CollisionHelper.SHAPE_OVERHANG);
        assertEquals(0, flags[BlockID.STONE]);
        assertEquals(0, flags[BlockID.WATER]);
    }

    private static void assertSameBlocks(Block[] expected, Block[] actual, AxisAlignedBB box, AxisAlignedBB scan) {
        String context = " box=" + box + " scan=" + scan + "\n expected=" + describe(expected) + "\n actual=" + describe(actual);
        assertEquals(expected.length, actual.length, "count" + context);
        for (int i = 0; i < expected.length; i++) {
            Block e = expected[i];
            Block a = actual[i];
            assertEquals(e.getClass(), a.getClass(), "class #" + i + context);
            assertEquals(e.getId(), a.getId(), "id #" + i + context);
            assertEquals(e.getDamage(), a.getDamage(), "meta #" + i + context);
            assertEquals(e.x, a.x, "x #" + i + context);
            assertEquals(e.y, a.y, "y #" + i + context);
            assertEquals(e.z, a.z, "z #" + i + context);
            assertSame(e.level, a.level, "level #" + i + context);
            assertEquals(e.layer, a.layer, "layer #" + i + context);
        }
    }

    private static String describe(Block[] blocks) {
        StringBuilder text = new StringBuilder("[");
        for (Block block : blocks) {
            text.append(block.getClass().getSimpleName()).append('@').append((int) block.x).append(',')
                    .append((int) block.y).append(',').append((int) block.z).append(':').append(block.getDamage()).append(' ');
        }
        return text.append(']').toString();
    }

    /** A mocked level over real LevelDB chunks -3..2 x -3..2, each with real sections. */
    static final class World {
        final Level level;
        final LevelDBProvider provider;
        final LevelDBChunk[][] chunks = new LevelDBChunk[6][6];

        private World() {
            level = mock(Level.class);
            provider = mock(LevelDBProvider.class);
            when(level.getMinBlockY()).thenReturn(0);
            when(level.getMaxBlockY()).thenReturn(255);
            when(level.isYInRange(anyInt())).thenCallRealMethod();
            when(level.getProvider()).thenReturn(provider);
            when(provider.getLevel()).thenReturn(level);
            when(level.getDimensionData()).thenReturn(DimensionData.LEGACY_DIMENSION);
            when(level.getChunkIfLoaded(anyInt(), anyInt())).thenAnswer(inv -> chunk(inv.getArgument(0), inv.getArgument(1)));
            when(level.getChunk(anyInt(), anyInt())).thenAnswer(inv -> chunk(inv.getArgument(0), inv.getArgument(1)));
            when(level.getChunk(anyInt(), anyInt(), anyBoolean())).thenAnswer(inv -> chunk(inv.getArgument(0), inv.getArgument(1)));
            when(level.getBlock(any(), anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean())).thenCallRealMethod();
            when(level.getBlock(any(), anyInt(), anyInt(), anyInt(), anyBoolean())).thenCallRealMethod();
            when(level.getBlock(anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean())).thenCallRealMethod();
            when(level.getBlock(anyInt(), anyInt(), anyInt(), anyInt())).thenCallRealMethod();
            when(level.getBlock(anyInt(), anyInt(), anyInt(), anyBoolean())).thenCallRealMethod();
            when(level.getBlock(anyInt(), anyInt(), anyInt())).thenCallRealMethod();
            when(level.getBlock(any(Vector3.class))).thenCallRealMethod();
            when(level.getBlock(any(Vector3.class), anyInt())).thenCallRealMethod();
            when(level.getBlock(any(Vector3.class), anyBoolean())).thenCallRealMethod();
            when(level.getBlock(any(Vector3.class), anyInt(), anyBoolean())).thenCallRealMethod();
            when(level.getBlockIdAt(anyInt(), anyInt(), anyInt())).thenCallRealMethod();
            when(level.getBlockIdAt(anyInt(), anyInt(), anyInt(), anyInt())).thenCallRealMethod();
            when(level.getBlockDataAt(anyInt(), anyInt(), anyInt())).thenCallRealMethod();
            when(level.getBlockDataAt(anyInt(), anyInt(), anyInt(), anyInt())).thenCallRealMethod();
        }

        World(Random random) {
            this();
            for (int cx = -3; cx <= 2; cx++) {
                for (int cz = -3; cz <= 2; cz++) {
                    ChunkSection[] sections = new ChunkSection[16];
                    for (int sy = 1; sy <= 5; sy++) {
                        if (random.nextInt(9) == 0) {
                            continue; // an empty section between filled ones
                        }
                        StateBlockStorage storage = new StateBlockStorage();
                        for (int i = 0; i < 4096; i++) {
                            if (random.nextInt(100) < 55) {
                                continue;
                            }
                            int id = PALETTE[random.nextInt(PALETTE.length)];
                            int meta = random.nextInt(8) == 0 ? random.nextInt(16) : 0;
                            storage.set(i, BlockStateSnapshot.builder().legacyId(id).legacyData(meta).build());
                        }
                        sections[sy] = new LevelDBChunkSection(sy, new StateBlockStorage[]{storage}, false);
                    }
                    chunks[cx + 3][cz + 3] = new LevelDBChunk(provider, cx, cz, sections, null, null, null, null, null, ChunkState.FINISHED);
                }
            }
        }

        static World flat(int id, int floorY) {
            World world = new World();
            for (int cx = -3; cx <= 2; cx++) {
                for (int cz = -3; cz <= 2; cz++) {
                    StateBlockStorage storage = new StateBlockStorage();
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            storage.set(x, floorY & 0xF, z, BlockStateSnapshot.builder().legacyId(id).legacyData(0).build());
                        }
                    }
                    ChunkSection[] sections = new ChunkSection[16];
                    sections[floorY >> 4] = new LevelDBChunkSection(floorY >> 4, new StateBlockStorage[]{storage}, false);
                    world.chunks[cx + 3][cz + 3] = new LevelDBChunk(world.provider, cx, cz, sections, null, null, null, null, null, ChunkState.FINISHED);
                }
            }
            return world;
        }

        /** A level where every cell holds the same block; neighbours for the shape probe. */
        static Level uniform(int id) {
            Level level = mock(Level.class);
            when(level.getMinBlockY()).thenReturn(0);
            when(level.getMaxBlockY()).thenReturn(255);
            org.mockito.stubbing.Answer<Block> answer = inv -> {
                Object[] args = inv.getArguments();
                int x, y, z;
                if (args[0] instanceof Vector3 v) {
                    x = v.getFloorX();
                    y = v.getFloorY();
                    z = v.getFloorZ();
                } else if (args[0] instanceof FullChunk || args[0] == null) {
                    x = (Integer) args[1];
                    y = (Integer) args[2];
                    z = (Integer) args[3];
                } else {
                    x = (Integer) args[0];
                    y = (Integer) args[1];
                    z = (Integer) args[2];
                }
                return Block.get(id, 0, level, x, y, z, 0);
            };
            when(level.getBlock(any(), anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean())).thenAnswer(answer);
            when(level.getBlock(any(), anyInt(), anyInt(), anyInt(), anyBoolean())).thenAnswer(answer);
            when(level.getBlock(anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean())).thenAnswer(answer);
            when(level.getBlock(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(answer);
            when(level.getBlock(anyInt(), anyInt(), anyInt(), anyBoolean())).thenAnswer(answer);
            when(level.getBlock(anyInt(), anyInt(), anyInt())).thenAnswer(answer);
            when(level.getBlock(any(Vector3.class))).thenAnswer(answer);
            when(level.getBlock(any(Vector3.class), anyInt())).thenAnswer(answer);
            when(level.getBlock(any(Vector3.class), anyBoolean())).thenAnswer(answer);
            when(level.getBlock(any(Vector3.class), anyInt(), anyBoolean())).thenAnswer(answer);
            when(level.getBlockIdAt(anyInt(), anyInt(), anyInt())).thenReturn(id);
            when(level.getBlockIdAt(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(id);
            when(level.getBlockDataAt(anyInt(), anyInt(), anyInt())).thenReturn(0);
            when(level.getBlockDataAt(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(0);
            return level;
        }

        /** Writes one block through the section storage, as every block write ends up doing. */
        void set(int x, int y, int z, int id) {
            LevelDBChunk chunk = chunk(x >> 4, z >> 4);
            if (chunk == null || y < 0 || y > 255) {
                return;
            }
            ChunkSection section = chunk.getSection(y >> 4);
            LevelDBChunkSection leveldb;
            if (section instanceof LevelDBChunkSection existing) {
                leveldb = existing;
            } else {
                leveldb = new LevelDBChunkSection(y >> 4, new StateBlockStorage[]{new StateBlockStorage()}, false);
                replaceSection(chunk, y >> 4, leveldb);
            }
            leveldb.getStorages()[0].set(x & 0xF, y & 0xF, z & 0xF,
                    BlockStateSnapshot.builder().legacyId(id).legacyData(0).build());
        }

        /** Replaces a whole section object, as loading or creating a section does. */
        void replaceSectionAt(int x, int y, int z, Random random) {
            LevelDBChunk chunk = chunk(x >> 4, z >> 4);
            if (chunk == null || y < 0 || y > 255) {
                return;
            }
            if (random.nextInt(4) == 0) {
                replaceSection(chunk, y >> 4, cn.nukkit.level.format.generic.EmptyChunkSection.bySectionY(y >> 4));
                return;
            }
            StateBlockStorage storage = new StateBlockStorage();
            for (int i = 0; i < 4096; i++) {
                if (random.nextInt(100) < 60) {
                    continue;
                }
                storage.set(i, BlockStateSnapshot.builder().legacyId(PALETTE[random.nextInt(PALETTE.length)]).legacyData(0).build());
            }
            replaceSection(chunk, y >> 4, new LevelDBChunkSection(y >> 4, new StateBlockStorage[]{storage}, false));
        }

        /** Unload and load again: a new chunk object with the same sections. */
        void reloadChunkAt(int x, int z) {
            int cx = x >> 4;
            int cz = z >> 4;
            LevelDBChunk old = chunk(cx, cz);
            if (old == null) {
                return;
            }
            ChunkSection[] sections = new ChunkSection[16];
            for (int sy = 0; sy < 16; sy++) {
                ChunkSection section = old.getSection(sy);
                sections[sy] = section instanceof LevelDBChunkSection ? section : null;
            }
            chunks[cx + 3][cz + 3] = new LevelDBChunk(provider, cx, cz, sections, null, null, null, null, null, ChunkState.FINISHED);
        }

        static void replaceSection(LevelDBChunk chunk, int sectionY, ChunkSection section) {
            try {
                java.lang.reflect.Field field = cn.nukkit.level.format.generic.BaseChunk.class.getDeclaredField("sections");
                field.setAccessible(true);
                ((ChunkSection[]) field.get(chunk))[sectionY] = section;
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }

        LevelDBChunk chunk(int cx, int cz) {
            if (cx < -3 || cx > 2 || cz < -3 || cz > 2) {
                return null;
            }
            return chunks[cx + 3][cz + 3];
        }

        CollisionHelper helperAt(double x, double z) {
            Entity entity = mock(Entity.class);
            entity.chunk = chunk((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
            when(entity.getLevel()).thenReturn(level);
            return new CollisionHelper(entity);
        }
    }
}
