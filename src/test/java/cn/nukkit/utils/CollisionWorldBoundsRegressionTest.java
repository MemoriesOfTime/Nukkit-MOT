package cn.nukkit.utils;

import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.DimensionData;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunkSection;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.math.SimpleAxisAlignedBB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Native baseline regression: mocked construction, actual fields and complete real bounds getter path. */
class CollisionWorldBoundsRegressionTest {
    private static final AxisAlignedBB BOX = new SimpleAxisAlignedBB(5, 64, 5, 6, 65.6, 6);
    private static final AxisAlignedBB SCAN = BOX.grow(.5, .5, .5);

    @BeforeAll static void init() { MockServer.init(); }

    @Test void loweringRoofMustDropCachedFireWhenClampedRangeStillIntersectsWorld() throws Exception {
        Fixture fixture = new Fixture();
        assertTrue(hasFire(fixture.warm.getCollisionCandidates(SCAN, BOX)));
        fixture.world.level.setDimensionData(new DimensionData(0, 0, 64));
        assertEquals(64, fixture.world.provider.getMaxBlockY());
        assertEquals(64, fixture.world.level.getMaxBlockY());
        assertNonemptyClamp(fixture.world.level);
        Block[] warmed = fixture.warm.getCollisionCandidates(SCAN, BOX);
        Block[] cold = fixture.cold().getCollisionCandidates(SCAN, BOX);
        assertFalse(hasFire(cold));
        print("lower-roof", fixture.world.level, warmed, cold);
        assertArrayEquals(signature(cold), signature(warmed), "warm cache must respect new roof");
    }

    @Test void raisingFloorMustDropCachedFireWhenClampedRangeStillIntersectsWorld() throws Exception {
        Fixture fixture = new Fixture();
        assertTrue(hasFire(fixture.warm.getCollisionCandidates(SCAN, BOX)));
        fixture.world.level.setDimensionData(new DimensionData(0, 66, 255));
        assertEquals(66, fixture.world.provider.getMinBlockY());
        assertEquals(66, fixture.world.level.getMinBlockY());
        assertNonemptyClamp(fixture.world.level);
        Block[] warmed = fixture.warm.getCollisionCandidates(SCAN, BOX);
        Block[] cold = fixture.cold().getCollisionCandidates(SCAN, BOX);
        assertFalse(hasFire(cold));
        print("raise-floor", fixture.world.level, warmed, cold);
        assertArrayEquals(signature(cold), signature(warmed), "warm cache must respect new floor");
    }

    @Test void alignedRoofMustDropCachedFireWithNonemptyClampedRange() throws Exception {
        Fixture fixture = new Fixture();
        assertTrue(hasFire(fixture.warm.getCollisionCandidates(SCAN, BOX)));
        DimensionData changed = new DimensionData(0, 0, 63);
        assertEquals(64, changed.getHeight());
        fixture.world.level.setDimensionData(changed);
        assertEquals(63, fixture.world.level.getMaxBlockY());
        assertNonemptyClamp(fixture.world.level);
        Block[] warmed = fixture.warm.getCollisionCandidates(SCAN, BOX);
        Block[] cold = fixture.cold().getCollisionCandidates(SCAN, BOX);
        assertFalse(hasFire(cold));
        print("aligned-roof", fixture.world.level, warmed, cold);
        assertArrayEquals(signature(cold), signature(warmed), "aligned roof must invalidate warm candidates");
    }

    @Test void alignedFloorMustDropCachedFireWithNonemptyClampedRange() throws Exception {
        Fixture fixture = new Fixture(61, 63);
        AxisAlignedBB box = new SimpleAxisAlignedBB(5, 62.2, 5, 6, 64.0, 6);
        AxisAlignedBB scan = box.grow(.5, .5, .5);
        assertTrue(hasFire(fixture.warm.getCollisionCandidates(scan, box), 63));
        DimensionData changed = new DimensionData(0, 64, 255);
        assertEquals(192, changed.getHeight());
        fixture.world.level.setDimensionData(changed);
        assertEquals(64, fixture.world.level.getMinBlockY());
        assertTrue(Math.max(NukkitMath.floorDouble(scan.getMinY()), fixture.world.level.getMinBlockY())
                <= Math.min(NukkitMath.floorDouble(scan.getMaxY()), fixture.world.level.getMaxBlockY()));
        Block[] warmed = fixture.warm.getCollisionCandidates(scan, box);
        Block[] cold = fixture.cold().getCollisionCandidates(scan, box);
        assertFalse(hasFire(cold, 63));
        print("aligned-floor", fixture.world.level, scan, warmed, cold);
        assertArrayEquals(signature(cold), signature(warmed), "aligned floor must invalidate warm candidates");
    }

    @Test void unchangedBoundsKeepWarmAndColdAnswersEqual() throws Exception {
        Fixture fixture = new Fixture();
        fixture.warm.getCollisionCandidates(SCAN, BOX);
        assertArrayEquals(signature(fixture.cold().getCollisionCandidates(SCAN, BOX)),
                signature(fixture.warm.getCollisionCandidates(SCAN, BOX)));
    }

    @Test void newDimensionIdentityWithEqualBoundsKeepsSameAnswer() throws Exception {
        Fixture fixture = new Fixture();
        fixture.warm.getCollisionCandidates(SCAN, BOX);
        DimensionData old = fixture.world.level.getDimensionData();
        fixture.world.level.setDimensionData(new DimensionData(0, 0, 255));
        assertNotSame(old, fixture.world.level.getDimensionData());
        assertArrayEquals(signature(fixture.cold().getCollisionCandidates(SCAN, BOX)),
                signature(fixture.warm.getCollisionCandidates(SCAN, BOX)));
    }

    @Test void movedBoxAfterBoundsChangeUsesColdScanAndHonorsRoof() throws Exception {
        Fixture fixture = new Fixture();
        fixture.warm.getCollisionCandidates(SCAN, BOX);
        fixture.world.level.setDimensionData(new DimensionData(0, 0, 64));
        AxisAlignedBB moved = BOX.getOffsetBoundingBox(.1, 0, 0);
        AxisAlignedBB movedScan = moved.grow(.5, .5, .5);
        Block[] warmed = fixture.warm.getCollisionCandidates(movedScan, moved);
        Block[] cold = fixture.cold().getCollisionCandidates(movedScan, moved);
        assertFalse(hasFire(warmed));
        assertArrayEquals(signature(cold), signature(warmed));
    }

    @Test void emptyClampReturnsEmptyBeforeOldLateCacheHit() throws Exception {
        Fixture fixture = new Fixture();
        fixture.warm.getCollisionCandidates(SCAN, BOX);
        fixture.world.level.setDimensionData(new DimensionData(0, 0, 62));
        assertTrue(Math.max(NukkitMath.floorDouble(SCAN.getMinY()), fixture.world.level.getMinBlockY())
                > Math.min(NukkitMath.floorDouble(SCAN.getMaxY()), fixture.world.level.getMaxBlockY()));
        assertEquals(0, fixture.warm.getCollisionCandidates(SCAN, BOX).length);
        assertEquals(0, fixture.cold().getCollisionCandidates(SCAN, BOX).length);
    }

    @Test void productionReadsEachWorldBoundOncePerMissHitAndBoundsChange() throws Exception {
        Fixture fixture = new Fixture();
        AtomicInteger minReads = new AtomicInteger();
        AtomicInteger maxReads = new AtomicInteger();
        doAnswer(invocation -> { minReads.incrementAndGet(); return invocation.callRealMethod(); })
                .when(fixture.world.level).getMinBlockY();
        doAnswer(invocation -> { maxReads.incrementAndGet(); return invocation.callRealMethod(); })
                .when(fixture.world.level).getMaxBlockY();
        for (int query = 0; query < 3; query++) {
            if (query == 2) fixture.world.level.setDimensionData(new DimensionData(0, 0, 64));
            minReads.set(0);
            maxReads.set(0);
            fixture.warm.getCollisionCandidates(SCAN, BOX);
            assertEquals(1, minReads.get(), "min getter reads for query " + query);
            assertEquals(1, maxReads.get(), "max getter reads for query " + query);
        }
    }

    @Test void directCacheWrappersCaptureAndValidateChangedWorldBounds() throws Exception {
        Fixture fixture = new Fixture();
        assertTrue(hasFire(fixture.warm.getCollisionCandidates(SCAN, BOX)));
        CollisionHelper.ScanCache cache = fixture.warm.scanCache();
        LevelDBChunk chunk = fixture.world.chunks[3][3];
        AtomicInteger minReads = new AtomicInteger();
        AtomicInteger maxReads = new AtomicInteger();
        doAnswer(invocation -> { minReads.incrementAndGet(); return invocation.callRealMethod(); })
                .when(fixture.world.level).getMinBlockY();
        doAnswer(invocation -> { maxReads.incrementAndGet(); return invocation.callRealMethod(); })
                .when(fixture.world.level).getMaxBlockY();
        assertTrue(cache.holds(fixture.world.level, chunk, SCAN, BOX));
        int warmMinReads = minReads.get();
        int warmMaxReads = maxReads.get();

        fixture.world.level.setDimensionData(new DimensionData(0, 0, 64));
        minReads.set(0);
        maxReads.set(0);
        assertFalse(cache.holds(fixture.world.level, chunk, SCAN, BOX), "direct hit must reject changed roof");
        assertEquals(1, warmMinReads);
        assertEquals(1, warmMaxReads);
        assertEquals(1, minReads.get());
        assertEquals(1, maxReads.get());

        minReads.set(0);
        maxReads.set(0);
        cache.begin(fixture.world.level);
        assertEquals(1, minReads.get());
        assertEquals(1, maxReads.get());
        LevelDBChunkSection section = (LevelDBChunkSection) chunk.getSection(4);
        cache.stamp(0, 0, 4, chunk, section, section.getBlockVersion(0));
        cache.commit(SCAN, BOX);
        assertTrue(cache.holds(fixture.world.level, chunk, SCAN, BOX), "direct begin must capture new roof");

        fixture.world.level.setDimensionData(new DimensionData(0, 10, 64));
        minReads.set(0);
        maxReads.set(0);
        assertFalse(cache.holds(fixture.world.level, chunk, SCAN, BOX), "direct hit must reject changed floor");
        assertEquals(1, minReads.get());
        assertEquals(1, maxReads.get());
    }

    @Test void nullableHoldWrapperRetainsShortCircuitAndEmptyCacheContract() {
        CollisionHelper.ScanCache cache = new CollisionHelper.ScanCache();
        assertFalse(cache.holds(null, null, null, null));
        cache.begin(null);
        assertFalse(cache.holds(null, null, null, null));
        // Isolated bookkeeping contract, as used by the existing null-level retention fixture.
        cache.commit(SCAN, BOX);
        assertTrue(cache.holds(null, null, SCAN, BOX));
    }

    private static final class Fixture {
        final CollisionCandidatesTest.World world;
        final CollisionHelper warm;

        Fixture() throws Exception {
            this(63, 65);
        }

        Fixture(int floorY, int fireY) throws Exception {
            world = CollisionCandidatesTest.World.flat(BlockID.STONE, floorY);
            assertEquals(Level.class, world.level.getClass());
            assertEquals(LevelDBProvider.class, world.provider.getClass());
            // Do not stub min/max answers: Level.requireProvider reads its actual provider field,
            // and native provider getters read their actual level field and its actual DimensionData.
            set(Level.class, world.level, "provider", world.provider);
            set(Level.class, world.level, "dimensionData", new DimensionData(0, 0, 255));
            set(LevelDBProvider.class, world.provider, "level", world.level);
            doCallRealMethod().when(world.level).getProvider();
            doCallRealMethod().when(world.level).requireProvider();
            doCallRealMethod().when(world.level).getMinBlockY();
            doCallRealMethod().when(world.level).getMaxBlockY();
            doCallRealMethod().when(world.level).getDimensionData();
            doCallRealMethod().when(world.level).setDimensionData(any(DimensionData.class));
            doCallRealMethod().when(world.provider).getLevel();
            doCallRealMethod().when(world.provider).getMinBlockY();
            doCallRealMethod().when(world.provider).getMaxBlockY();
            assertSame(world.level, world.provider.getLevel());
            assertEquals(0, world.level.getMinBlockY());
            assertEquals(255, world.level.getMaxBlockY());
            world.set(5, fireY, 5, BlockID.FIRE);
            warm = cold();
        }

        CollisionHelper cold() { return world.helperAt(5.5, 5.5); }
    }

    private static void set(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static boolean hasFire(Block[] blocks) {
        return hasFire(blocks, 65);
    }

    private static boolean hasFire(Block[] blocks, int y) {
        return Arrays.stream(blocks).anyMatch(b -> b.getId() == BlockID.FIRE && b.getFloorY() == y);
    }

    private static String[] signature(Block[] blocks) {
        return Arrays.stream(blocks).map(b -> b.getClass().getName() + ':' + b.getId() + ':' + b.getDamage()
                + '@' + b.getFloorX() + ',' + b.getFloorY() + ',' + b.getFloorZ()).toArray(String[]::new);
    }

    private static void assertNonemptyClamp(Level level) {
        assertTrue(Math.max(NukkitMath.floorDouble(SCAN.getMinY()), level.getMinBlockY())
                <= Math.min(NukkitMath.floorDouble(SCAN.getMaxY()), level.getMaxBlockY()));
    }

    private static void print(String name, Level level, Block[] warmed, Block[] cold) {
        print(name, level, SCAN, warmed, cold);
    }

    private static void print(String name, Level level, AxisAlignedBB scan, Block[] warmed, Block[] cold) {
        System.out.println(name + " world=" + level.getMinBlockY() + ".." + level.getMaxBlockY()
                + " clamp=" + Math.max(NukkitMath.floorDouble(scan.getMinY()), level.getMinBlockY())
                + ".." + Math.min(NukkitMath.floorDouble(scan.getMaxY()), level.getMaxBlockY())
                + " warm=" + Arrays.toString(signature(warmed)) + " cold=" + Arrays.toString(signature(cold)));
    }
}
