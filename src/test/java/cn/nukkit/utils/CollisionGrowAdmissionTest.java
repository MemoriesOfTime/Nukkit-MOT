package cn.nukkit.utils;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.entity.Entity;
import cn.nukkit.level.DimensionData;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.generic.BaseChunk;
import cn.nukkit.level.format.leveldb.structure.ChunkState;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunkSection;
import cn.nukkit.level.format.leveldb.structure.StateBlockStorage;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.nbt.tag.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Both versions execute actual getCollisionBlocks, with native chunks and unchanged callback filtering. */
class CollisionGrowAdmissionTest {
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

    private static CollisionGrowFixture recordingFixture() throws Exception {
        return new CollisionGrowFixture(false, RecordingEntity.class);
    }

    private static void sameCold(CollisionGrowFixture f, Block[] answer) {
        assertArrayEquals(signature(new CollisionHelper(f.entity).getCollisionBlocks()), signature(answer));
    }

    private static String[] signature(Block[] blocks) {
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
}
