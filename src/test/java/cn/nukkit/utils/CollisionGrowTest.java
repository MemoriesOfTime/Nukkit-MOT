package cn.nukkit.utils;

import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.DimensionData;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.generic.BaseChunk;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import cn.nukkit.level.format.leveldb.structure.ChunkState;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunkSection;
import cn.nukkit.level.format.leveldb.structure.StateBlockStorage;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.nbt.tag.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Both versions execute actual getCollisionBlocks, with native chunks and unchanged callback filtering. */
class CollisionGrowTest {
    private static final AxisAlignedBB BOX = new SimpleAxisAlignedBB(5, 64, 5, 6, 65.6, 6);
    private static final AxisAlignedBB SCAN = BOX.grow(.5, .5, .5);

    @BeforeAll static void init() { MockServer.init(); }

    @Test void warmAnswerMatchesColdAndMaterialisesFreshBlocks() throws Exception {
        CollisionGrowFixture f = new CollisionGrowFixture(false);
        f.write(5, 64, 5, BlockID.STONE);
        Block[] first = f.helper.getCollisionBlocks();
        Block[] second = f.helper.getCollisionBlocks();
        assertEquals(1, first.length);
        assertEquals(1, second.length);
        assertNotSame(first[0], second[0], "warm hit must materialise fresh blocks");
        sameCold(f, second);
    }

    @Test void movementRescansPreviouslyExcludedSolidBlock() throws Exception {
        CollisionGrowFixture f = new CollisionGrowFixture(false);
        f.write(6, 64, 5, BlockID.STONE);
        assertEquals(0, f.helper.getCollisionBlocks().length);
        f.entity.box(new SimpleAxisAlignedBB(5.25, 64, 5, 6.25, 65.6, 6));
        Block[] moved = f.helper.getCollisionBlocks();
        assertEquals(1, moved.length);
        sameCold(f, moved);
    }

    @Test void motionChangeUsesOriginalRescan() throws Exception {
        CollisionGrowFixture f = new CollisionGrowFixture(false);
        f.write(5, 64, 5, BlockID.STONE);
        LevelDBChunkSection section = spy((LevelDBChunkSection) f.chunk.getSection(4));
        assertEquals(LevelDBChunkSection.class, section.getClass());
        f.chunk.getSections()[4] = section;
        f.helper.getCollisionBlocks();
        clearInvocations(section);
        f.entity.motionX = 2;
        Block[] moved = f.helper.getCollisionBlocks();
        verify(section).getBlockStatePairs(anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(),
                any(long[].class), anyInt(), anyInt(), anyInt());
        sameCold(f, moved);
    }

    @Test void alignedWorldRoofInvalidatesActualCollisionBlocks() throws Exception {
        CollisionGrowFixture f = new CollisionGrowFixture(false);
        f.write(5, 64, 5, BlockID.STONE);
        assertEquals(1, f.helper.getCollisionBlocks().length);
        f.level.setDimensionData(new DimensionData(0, 0, 63));
        Block[] changed = f.helper.getCollisionBlocks();
        assertEquals(0, changed.length);
        sameCold(f, changed);
    }

    @Test void alignedWorldFloorInvalidatesActualCollisionBlocks() throws Exception {
        CollisionGrowFixture f = new CollisionGrowFixture(false);
        f.entity.box(new SimpleAxisAlignedBB(5, 62.2, 5, 6, 64, 6));
        assertEquals(1, f.helper.getCollisionBlocks().length);
        f.level.setDimensionData(new DimensionData(0, 64, 255));
        Block[] changed = f.helper.getCollisionBlocks();
        assertEquals(0, changed.length);
        sameCold(f, changed);
    }

    @Test void layerZeroVersionChangeCannotReturnPreviousBlock() throws Exception {
        CollisionGrowFixture f = new CollisionGrowFixture(false);
        f.write(5, 64, 5, BlockID.STONE);
        assertEquals(BlockID.STONE, f.helper.getCollisionBlocks()[0].getId());
        f.write(5, 64, 5, BlockID.GOLD_BLOCK);
        Block[] changed = f.helper.getCollisionBlocks();
        assertEquals(BlockID.GOLD_BLOCK, changed[0].getId());
        sameCold(f, changed);
    }

    @Test void sameVersionSectionSlotReplacementRescans() throws Exception {
        CollisionGrowFixture f = new CollisionGrowFixture(false);
        f.write(5, 64, 5, BlockID.STONE);
        f.helper.getCollisionBlocks();
        LevelDBChunkSection old = (LevelDBChunkSection) f.chunk.getSection(4);
        LevelDBChunkSection replacement = replacement(f, BlockID.GOLD_BLOCK);
        assertEquals(old.getBlockVersion(0), replacement.getBlockVersion(0));
        f.chunk.getSections()[4] = replacement;
        Block[] changed = f.helper.getCollisionBlocks();
        assertEquals(BlockID.GOLD_BLOCK, changed[0].getId());
        sameCold(f, changed);
    }

    @Test void sectionArrayReplacementReadsCurrentSlot() throws Exception {
        CollisionGrowFixture f = new CollisionGrowFixture(false);
        f.write(5, 64, 5, BlockID.STONE);
        f.helper.getCollisionBlocks();
        var sections = f.chunk.getSections().clone();
        sections[4] = replacement(f, BlockID.GOLD_BLOCK);
        CollisionGrowFixture.set(BaseChunk.class, f.chunk, "sections", sections);
        Block[] changed = f.helper.getCollisionBlocks();
        assertEquals(BlockID.GOLD_BLOCK, changed[0].getId());
        sameCold(f, changed);
    }

    @Test void chunkHintReplacementRescans() throws Exception {
        CollisionGrowFixture f = new CollisionGrowFixture(false);
        f.write(5, 64, 5, BlockID.STONE);
        f.helper.getCollisionBlocks();
        var sections = f.chunk.getSections().clone();
        sections[4] = replacement(f, BlockID.GOLD_BLOCK);
        f.entity.chunk = new LevelDBChunk(f.provider, 0, 0, sections, null, null, null, null, null, ChunkState.FINISHED);
        Block[] changed = f.helper.getCollisionBlocks();
        assertEquals(BlockID.GOLD_BLOCK, changed[0].getId());
        sameCold(f, changed);
    }

    @Test void customGrowKeepsItsVirtualCallsAndGeometry() throws Exception {
        CollisionGrowFixture f = new CollisionGrowFixture(false);
        f.write(5, 64, 5, BlockID.STONE);
        f.helper.getCollisionBlocks();
        RecordingBox box = new RecordingBox();
        f.entity.box(box);
        f.entity.motionX = 1000;
        assertEquals(0, f.helper.getCollisionBlocks().length);
        assertEquals(0, f.helper.getCollisionBlocks().length);
        assertEquals(2, box.calls);
        assertEquals(64, box.lastX);
        assertEquals(.5, box.lastY);
        assertEquals(.5, box.lastZ);
    }

    @Test void virtualContextCountsAndOrderMatchOriginalWarmQuery() throws Exception {
        CollisionGrowFixture f = recordingFixture();
        RecordingEntity e = (RecordingEntity) f.entity;
        RecordingDimension dimension = new RecordingDimension(e);
        f.level.setDimensionData(dimension);
        f.helper.getCollisionBlocks();
        e.reset();
        f.helper.getCollisionBlocks();
        assertEquals(List.of("closed", "level", "box", "level", "closed", "min", "max"), e.events);
        print("warm-context", e.events);
    }

    @Test void contextMutationUsesGrowGeometryCapturedBeforeGetter() throws Exception {
        CollisionGrowFixture f = recordingFixture();
        f.write(5, 64, 5, BlockID.STONE);
        f.write(8, 64, 5, BlockID.STONE);
        f.helper.getCollisionBlocks();
        RecordingEntity e = (RecordingEntity) f.entity;
        e.reset();
        e.onSecondLevel = () -> e.moveBox(3);
        Block[] changed = f.helper.getCollisionBlocks();
        assertEquals(0, changed.length);
        assertEquals(List.of("closed", "level", "box", "level", "closed"), e.events);
        print("mutated-box", e.events);
    }

    @Test void contextReplacementRetainsOriginalBoundingBoxObjectVariable() throws Exception {
        CollisionGrowFixture f = recordingFixture();
        f.write(5, 64, 5, BlockID.STONE);
        f.helper.getCollisionBlocks();
        RecordingEntity e = (RecordingEntity) f.entity;
        e.reset();
        e.onSecondLevel = () -> e.box(new SimpleAxisAlignedBB(8, 64, 5, 9, 65.6, 6));
        Block[] changed = f.helper.getCollisionBlocks();
        assertEquals(1, changed.length, "filter must keep the original local box object");
        assertEquals(List.of("closed", "level", "box", "level", "closed"), e.events);
        print("replaced-box", e.events);
    }

    @Test void closedToggleRetainsOriginalFallbackGetterOrder() throws Exception {
        CollisionGrowFixture f = recordingFixture();
        RecordingEntity e = (RecordingEntity) f.entity;
        e.reset();
        e.closeOnSecondCheck = true;
        assertEquals(0, f.helper.getCollisionBlocks().length);
        assertEquals(List.of("closed", "level", "box", "level", "closed", "level", "closed"), e.events);
        print("closed-toggle", e.events);
    }

    @Test void cacheResetInsideVirtualContextUsesSingleOriginalAdmission() throws Exception {
        CollisionGrowFixture f = recordingFixture();
        f.write(5, 64, 5, BlockID.STONE);
        f.helper.getCollisionBlocks();
        RecordingEntity e = (RecordingEntity) f.entity;
        e.reset();
        e.onSecondLevel = () -> {
            f.helper.scanCache().begin(f.level);
            f.write(5, 64, 5, BlockID.GOLD_BLOCK);
        };
        Block[] changed = f.helper.getCollisionBlocks();
        assertEquals(BlockID.GOLD_BLOCK, changed[0].getId());
        assertEquals(List.of("closed", "level", "box", "level", "closed"), e.events);
        print("cache-reset", e.events);
    }

    @Test void nestedQueryInsideVirtualContextPreservesReentrantOrder() throws Exception {
        CollisionGrowFixture f = recordingFixture();
        f.write(5, 64, 5, BlockID.STONE);
        f.helper.getCollisionBlocks();
        RecordingEntity e = (RecordingEntity) f.entity;
        e.reset();
        e.onSecondLevel = () -> {
            f.write(5, 64, 5, BlockID.GOLD_BLOCK);
            assertEquals(BlockID.GOLD_BLOCK, f.helper.getCollisionBlocks()[0].getId());
        };
        assertEquals(BlockID.GOLD_BLOCK, f.helper.getCollisionBlocks()[0].getId());
        assertEquals(List.of("closed", "level", "box", "level", "closed",
                "level", "box", "level", "closed", "closed"), e.events);
        print("reentrant-query", e.events);
    }

    @Test void dimensionBoundsMutationKeepsSingleObservedReadsAndCapturedScan() throws Exception {
        CollisionGrowFixture f = recordingFixture();
        f.write(5, 64, 5, BlockID.STONE);
        f.write(8, 64, 5, BlockID.STONE);
        f.helper.getCollisionBlocks();
        RecordingEntity e = (RecordingEntity) f.entity;
        RecordingDimension dimension = new RecordingDimension(e);
        f.level.setDimensionData(dimension);
        f.helper.getCollisionBlocks();
        e.reset();
        dimension.onMin = () -> e.moveBox(3);
        assertEquals(0, f.helper.getCollisionBlocks().length);
        assertEquals(List.of("closed", "level", "box", "level", "closed", "min", "max"), e.events);
        print("bounds-mutation", e.events);
    }

    @Test void customBoundsMismatchRescansUsingSingleObservedContext() throws Exception {
        CollisionGrowFixture f = recordingFixture();
        f.write(5, 64, 5, BlockID.STONE);
        RecordingEntity e = (RecordingEntity) f.entity;
        f.level.setDimensionData(new RecordingDimension(e));
        assertEquals(1, f.helper.getCollisionBlocks().length);
        f.level.setDimensionData(new RecordingDimension(e, 63));
        e.reset();
        assertEquals(0, f.helper.getCollisionBlocks().length);
        assertEquals(List.of("closed", "level", "box", "level", "closed", "min", "max"), e.events);
        print("custom-bounds-rescan", e.events);
    }

    @Test void nonfiniteAndFiniteOverflowRetainColdValidation() throws Exception {
        CollisionGrowFixture f = recordingFixture();
        RecordingEntity e = (RecordingEntity) f.entity;
        e.reset();
        e.motionX = Double.NaN;
        assertEquals(0, f.helper.getCollisionBlocks().length);
        assertEquals(List.of("closed", "level", "box", "level", "closed", "level", "closed"), e.events);
        e.motionX = 0;
        e.box(new SimpleAxisAlignedBB(3e9, 64, 5, 3e9 + 1, 65.6, 6));
        assertThrows(IllegalArgumentException.class, f.helper::getCollisionBlocks);
    }

    /** Native baseline regression: mocked construction, actual fields and complete real bounds getter path. */
    @Test void loweringRoofMustDropCachedFireWhenClampedRangeStillIntersectsWorld() throws Exception {
        BoundsFixture fixture = new BoundsFixture();
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
        BoundsFixture fixture = new BoundsFixture();
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
        BoundsFixture fixture = new BoundsFixture();
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
        BoundsFixture fixture = new BoundsFixture(61, 63);
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
        BoundsFixture fixture = new BoundsFixture();
        fixture.warm.getCollisionCandidates(SCAN, BOX);
        assertArrayEquals(signature(fixture.cold().getCollisionCandidates(SCAN, BOX)),
                signature(fixture.warm.getCollisionCandidates(SCAN, BOX)));
    }

    @Test void newDimensionIdentityWithEqualBoundsKeepsSameAnswer() throws Exception {
        BoundsFixture fixture = new BoundsFixture();
        fixture.warm.getCollisionCandidates(SCAN, BOX);
        DimensionData old = fixture.world.level.getDimensionData();
        fixture.world.level.setDimensionData(new DimensionData(0, 0, 255));
        assertNotSame(old, fixture.world.level.getDimensionData());
        assertArrayEquals(signature(fixture.cold().getCollisionCandidates(SCAN, BOX)),
                signature(fixture.warm.getCollisionCandidates(SCAN, BOX)));
    }

    @Test void movedBoxAfterBoundsChangeUsesColdScanAndHonorsRoof() throws Exception {
        BoundsFixture fixture = new BoundsFixture();
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
        BoundsFixture fixture = new BoundsFixture();
        fixture.warm.getCollisionCandidates(SCAN, BOX);
        fixture.world.level.setDimensionData(new DimensionData(0, 0, 62));
        assertTrue(Math.max(NukkitMath.floorDouble(SCAN.getMinY()), fixture.world.level.getMinBlockY())
                > Math.min(NukkitMath.floorDouble(SCAN.getMaxY()), fixture.world.level.getMaxBlockY()));
        assertEquals(0, fixture.warm.getCollisionCandidates(SCAN, BOX).length);
        assertEquals(0, fixture.cold().getCollisionCandidates(SCAN, BOX).length);
    }

    @Test void productionReadsEachWorldBoundOncePerMissHitAndBoundsChange() throws Exception {
        BoundsFixture fixture = new BoundsFixture();
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
        BoundsFixture fixture = new BoundsFixture();
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

    private static CollisionGrowFixture recordingFixture() throws Exception {
        return new CollisionGrowFixture(false, RecordingEntity.class);
    }

    private static void sameCold(CollisionGrowFixture f, Block[] answer) {
        assertArrayEquals(compactSignature(new CollisionHelper(f.entity).getCollisionBlocks()), compactSignature(answer));
    }

    private static String[] compactSignature(Block[] blocks) {
        return Arrays.stream(blocks).map(b -> b.getId() + ":" + b.getDamage() + "@"
                + b.getFloorX() + ',' + b.getFloorY() + ',' + b.getFloorZ()).toArray(String[]::new);
    }

    private static LevelDBChunkSection replacement(CollisionGrowFixture f, int id) {
        StateBlockStorage storage = new StateBlockStorage();
        LevelDBChunkSection section = new LevelDBChunkSection(4, new StateBlockStorage[]{storage}, false);
        section.setParent(f.chunk);
        storage.set(5, 0, 5, cn.nukkit.level.format.leveldb.structure.BlockStateSnapshot.builder()
                .legacyId(id).legacyData(0).build());
        return section;
    }

    private static void print(String name, List<String> events) { System.out.println(name + "=" + events); }

    static final class RecordingEntity extends CollisionGrowFixture.ProbeEntity {
        List<String> events;
        int levelCalls, closedCalls;
        Runnable onSecondLevel;
        boolean closeOnSecondCheck;
        RecordingEntity(FullChunk chunk, CompoundTag tag) { super(chunk, tag); }
        void reset() { events = new ArrayList<>(); levelCalls = closedCalls = 0; }
        @Override public Level getLevel() {
            if (events != null) events.add("level");
            if (++levelCalls == 2 && onSecondLevel != null) onSecondLevel.run();
            return super.getLevel();
        }
        @Override public boolean isClosed() {
            if (events != null) events.add("closed");
            if (++closedCalls == 2 && closeOnSecondCheck) closed = true;
            return super.isClosed();
        }
        @Override public AxisAlignedBB getBoundingBox() {
            if (events != null) events.add("box");
            return super.getBoundingBox();
        }
        void moveBox(double dx) {
            boundingBox.setMinX(boundingBox.getMinX() + dx);
            boundingBox.setMaxX(boundingBox.getMaxX() + dx);
        }
    }

    static final class RecordingDimension extends DimensionData {
        final RecordingEntity entity;
        Runnable onMin;
        RecordingDimension(RecordingEntity entity) { this(entity, 255); }
        RecordingDimension(RecordingEntity entity, int maxY) { super(0, 0, maxY); this.entity = entity; }
        @Override public int getMinHeight() {
            if (entity.events != null) entity.events.add("min");
            if (onMin != null) onMin.run();
            return super.getMinHeight();
        }
        @Override public int getMaxHeight() {
            if (entity.events != null) entity.events.add("max");
            return super.getMaxHeight();
        }
    }

    static final class RecordingBox extends SimpleAxisAlignedBB {
        int calls;
        double lastX, lastY, lastZ;
        RecordingBox() { super(5, 64, 5, 6, 65.6, 6); }
        @Override public AxisAlignedBB grow(double x, double y, double z) {
            calls++; lastX = x; lastY = y; lastZ = z;
            return new SimpleAxisAlignedBB(8, 63.5, 4.5, 9, 66.1, 6.5);
        }
    }

    private static final class BoundsFixture {
        final CollisionCandidatesTest.World world;
        final CollisionHelper warm;

        BoundsFixture() throws Exception {
            this(63, 65);
        }

        BoundsFixture(int floorY, int fireY) throws Exception {
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
