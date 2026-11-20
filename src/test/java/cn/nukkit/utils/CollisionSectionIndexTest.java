package cn.nukkit.utils;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.DimensionData;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.ChunkSection;
import cn.nukkit.level.format.generic.BaseChunk;
import cn.nukkit.level.format.generic.EmptyChunkSection;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import cn.nukkit.level.format.leveldb.structure.*;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.SimpleAxisAlignedBB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CollisionSectionIndexTest {
    private static final AxisAlignedBB BOX = new SimpleAxisAlignedBB(5, 64, 5, 6, 65.6, 6);
    private static final AxisAlignedBB SCAN = BOX.grow(0.5, 0.5, 0.5);

    @BeforeAll
    static void init() { MockServer.init(); }

    @Test void unchangedHitAvoidsSectionGetterAndOffsetCalculation() {
        var world = CollisionCandidatesTest.World.flat(BlockID.STONE, 63);
        LevelDBChunk chunk = spy(world.chunk(0, 0));
        assertEquals(LevelDBChunk.class, chunk.getClass(), "inline spy must retain the standard chunk class");
        var cache = capture(world.level, chunk, 4);
        clearInvocations(chunk);
        for (int i = 0; i < 3; i++) assertTrue(cache.holds(world.level, chunk, SCAN, BOX));
        verify(chunk, never()).getSection(4);
        verify(chunk, never()).getSectionOffset();
    }

    @Test void emptySectionMaterializationBecomesVisibleWithoutMoving() {
        var world = CollisionCandidatesTest.World.flat(BlockID.STONE, 63);
        CollisionHelper helper = world.helperAt(5.5, 5.5);
        assertEquals(0, helper.getCollisionCandidates(SCAN, BOX).length);
        assertTrue(world.chunk(0, 0).getSection(4) instanceof EmptyChunkSection);
        world.set(5, 64, 5, BlockID.FIRE);
        Block[] actual = helper.getCollisionCandidates(SCAN, BOX);
        Block[] full = helper.getBlocksInBoundingBox(SCAN);
        assertTrue(Arrays.stream(actual).anyMatch(b -> b.getId() == BlockID.FIRE));
        assertArrayEquals(signature(CollisionHelper.filterCollisionBlocks(full, BOX, .5, .5, .5)),
                signature(CollisionHelper.filterCollisionBlocks(actual, BOX, .5, .5, .5)));
    }

    @Test void sameSectionBlockVersionChangeInvalidatesHit() {
        var world = CollisionCandidatesTest.World.flat(BlockID.STONE, 63);
        world.set(5, 64, 5, BlockID.STONE);
        LevelDBChunk chunk = world.chunk(0, 0);
        ChunkSection original = chunk.getSection(4);
        var cache = capture(world.level, chunk, 4);
        assertTrue(cache.holds(world.level, chunk, SCAN, BOX));
        world.set(5, 64, 5, BlockID.FIRE);
        assertSame(original, chunk.getSection(4));
        assertFalse(cache.holds(world.level, chunk, SCAN, BOX));
    }

    @Test void equalVersionSectionReplacementInvalidatesHit() {
        var world = CollisionCandidatesTest.World.flat(BlockID.STONE, 63);
        LevelDBChunk chunk = world.chunk(0, 0);
        var first = new LevelDBChunkSection(4, new StateBlockStorage[]{new StateBlockStorage()}, false);
        var second = new LevelDBChunkSection(4, new StateBlockStorage[]{new StateBlockStorage()}, false);
        assertEquals(first.getBlockVersion(0), second.getBlockVersion(0));
        CollisionCandidatesTest.World.replaceSection(chunk, 4, first);
        var cache = capture(world.level, chunk, 4);
        CollisionCandidatesTest.World.replaceSection(chunk, 4, second);
        assertFalse(cache.holds(world.level, chunk, SCAN, BOX));
    }

    @Test void arrayReplacementReadsCurrentSlotsWithoutTreatingArrayAsAuthority() throws Exception {
        var world = CollisionCandidatesTest.World.flat(BlockID.STONE, 63);
        LevelDBChunk chunk = world.chunk(0, 0);
        var cache = capture(world.level, chunk, 3);
        ChunkSection[] original = chunk.getSections();
        ChunkSection[] replacement = original.clone();
        setSections(chunk, replacement);
        assertTrue(cache.holds(world.level, chunk, SCAN, BOX), "same identities survive a new array");
        replacement[3] = EmptyChunkSection.bySectionY(3);
        assertFalse(cache.holds(world.level, chunk, SCAN, BOX), "new slot must invalidate");
        assertTrue(original[3] instanceof LevelDBChunkSection, "old array was not modified");
    }

    @Test void providerReattachmentKeepsChunkOffsetAndReplacementChunkInvalidates() {
        var world = CollisionCandidatesTest.World.flat(BlockID.STONE, 63);
        LevelDBChunk old = world.chunk(0, 0);
        var cache = capture(world.level, old, 3);
        LevelDBProvider provider = mock(LevelDBProvider.class);
        when(provider.getLevel()).thenReturn(world.level);
        old.setProvider(provider);
        when(world.level.getProvider()).thenReturn(provider);
        assertTrue(cache.holds(world.level, old, SCAN, BOX));
        LevelDBChunk fresh = new LevelDBChunk(provider, 0, 0, old.getSections().clone(),
                null, null, null, null, null, ChunkState.FINISHED);
        world.chunks[3][3] = fresh;
        assertFalse(cache.holds(world.level, fresh, SCAN, BOX));
        assertFalse(cache.holds(world.level, null, SCAN, BOX));
    }

    @Test void outOfIndexUsesExistingGetterClampAfterArrayShrinks() throws Exception {
        var world = CollisionCandidatesTest.World.flat(BlockID.STONE, 63);
        when(world.level.getServer()).thenReturn(Server.getInstance());
        LevelDBChunk chunk = spy(world.chunk(0, 0));
        assertEquals(LevelDBChunk.class, chunk.getClass());
        ChunkSection section = chunk.getSection(4);
        var cache = capture(world.level, chunk, 4);
        setSections(chunk, new ChunkSection[]{section});
        clearInvocations(chunk);
        assertTrue(cache.holds(world.level, chunk, SCAN, BOX), "same old getter clamp result");
        verify(chunk).getSection(4);
    }

    @Test void negativeOutOfIndexStillUsesExistingGetterFailure() {
        var world = CollisionCandidatesTest.World.flat(BlockID.STONE, 63);
        LevelDBChunk chunk = spy(world.chunk(0, 0));
        var cache = new CollisionHelper.ScanCache();
        cache.begin(world.level);
        cache.stamp(0, 0, -1, chunk, chunk.getSection(0), CollisionHelper.ScanCache.NO_VERSION);
        cache.commit(SCAN, BOX);
        clearInvocations(chunk);
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> cache.holds(world.level, chunk, SCAN, BOX));
        verify(chunk).getSection(-1);
    }

    @Test void minAndMaxDimensionSectionsRetainIdentityAndVersionGuards() {
        for (DimensionData dimension : new DimensionData[]{
                new DimensionData(0, -64, 319), new DimensionData(1, 0, 127), new DimensionData(2, 0, 255)}) {
            var world = CollisionCandidatesTest.World.flat(BlockID.STONE, 63);
            when(world.level.getDimensionData()).thenReturn(dimension);
            when(world.level.getMinBlockY()).thenReturn(dimension.getMinHeight());
            when(world.level.getMaxBlockY()).thenReturn(dimension.getMaxHeight());
            LevelDBChunk chunk = new LevelDBChunk(world.provider, 0, 0,
                    new ChunkSection[dimension.getHeight() >> 4], null, null, null, null, null, ChunkState.FINISHED);
            for (int sectionY : new int[]{dimension.getMinSectionY(), dimension.getMaxSectionY()}) {
                var cache = capture(world.level, chunk, sectionY);
                assertTrue(cache.holds(world.level, chunk, SCAN, BOX));
                chunk.getSections()[sectionY + chunk.getSectionOffset()] =
                        new LevelDBChunkSection(sectionY, new StateBlockStorage[]{new StateBlockStorage()}, false);
                assertFalse(cache.holds(world.level, chunk, SCAN, BOX));
            }
        }
    }

    @Test void growingStampStorageRetainsIndexesAndChecksLastSection() {
        var world = CollisionCandidatesTest.World.flat(BlockID.STONE, 63);
        LevelDBChunk chunk = world.chunk(0, 0);
        var cache = new CollisionHelper.ScanCache();
        cache.begin(world.level);
        for (int sectionY = 0; sectionY < 10; sectionY++) stamp(cache, chunk, sectionY);
        cache.commit(SCAN, BOX);
        assertTrue(cache.holds(world.level, chunk, SCAN, BOX));
        CollisionCandidatesTest.World.replaceSection(chunk, 9,
                new LevelDBChunkSection(9, new StateBlockStorage[]{new StateBlockStorage()}, false));
        assertFalse(cache.holds(world.level, chunk, SCAN, BOX));
    }

    @Test void onlyExactDimensionOrKnownLegacyIsEligible() {
        assertTrue(new OffsetFixture(new DimensionData(0, 0, 255)).chunk.hasStableSectionOffset());
        assertTrue(new OffsetFixture(DimensionData.LEGACY_DIMENSION).chunk.hasStableSectionOffset());
        assertFalse(new OffsetFixture(new FixedCustomDimension()).chunk.hasStableSectionOffset());
        assertFalse(new OffsetFixture(new MutableCustomDimension()).chunk.hasStableSectionOffset());
    }

    @Test void customDimensionKeepsOldGetterWithoutExtraOffsetOrArrayProbe() {
        OffsetFixture fixture = new OffsetFixture(new FixedCustomDimension());
        LevelDBChunk chunk = spy(fixture.chunk);
        assertEquals(LevelDBChunk.class, chunk.getClass());
        ChunkSection section = chunk.getSection(4);
        var cache = new CollisionHelper.ScanCache();
        cache.begin(fixture.world.level);
        clearInvocations(chunk);
        cache.stamp(0, 0, 4, chunk, section, CollisionHelper.ScanCache.NO_VERSION);
        verify(chunk, never()).getSectionOffset();
        verify(chunk, never()).getSections();
        cache.commit(SCAN, BOX);
        clearInvocations(chunk);
        assertTrue(cache.holds(fixture.world.level, chunk, SCAN, BOX));
        verify(chunk).getSection(4);
        verify(chunk).getSectionOffset();
        verify(chunk, never()).getSections();
    }

    @Test void capturedCustomDimensionRemainsIneligibleAfterProviderReattachment() {
        var custom = new MutableCustomDimension();
        OffsetFixture fixture = new OffsetFixture(custom);
        LevelDBProvider replacement = mock(LevelDBProvider.class);
        when(replacement.getLevel()).thenReturn(fixture.world.level);
        when(fixture.world.level.getDimensionData()).thenReturn(new DimensionData(0, 0, 255));
        fixture.chunk.setProvider(replacement);
        assertFalse(fixture.chunk.hasStableSectionOffset());
        custom.offset = 1;
        assertEquals(1, fixture.chunk.getSectionOffset(), "captured custom getter still controls the chunk");
    }

    @Test void capturedExactDimensionRemainsEligibleAfterProviderReattachment() {
        OffsetFixture fixture = new OffsetFixture(new DimensionData(0, 0, 255));
        var custom = new MutableCustomDimension();
        custom.offset = 1;
        when(fixture.world.level.getDimensionData()).thenReturn(custom);
        LevelDBProvider replacement = mock(LevelDBProvider.class);
        when(replacement.getLevel()).thenReturn(fixture.world.level);
        fixture.chunk.setProvider(replacement);
        assertTrue(fixture.chunk.hasStableSectionOffset());
        assertEquals(0, fixture.chunk.getSectionOffset(), "captured exact data controls the chunk");
    }

    @Test void overridingMutableSectionOffsetMustInvalidateWarmCandidates() throws Exception {
        var dimension = new MutableSectionOffset();
        OffsetProbe probe = new OffsetProbe(dimension);
        probe.warm();
        dimension.offset = 1;
        probe.changed("getSectionOffset");
    }

    @Test void overridingMutableMinSectionYMustInvalidateWarmCandidates() throws Exception {
        var dimension = new MutableMinSectionY();
        OffsetProbe probe = new OffsetProbe(dimension);
        probe.warm();
        dimension.minSectionY = -1;
        probe.changed("getMinSectionY");
    }

    @Test void unchangedCustomDimensionKeepsWarmAndColdAnswersEqual() throws Exception {
        OffsetProbe probe = new OffsetProbe(new MutableSectionOffset());
        probe.warm();
        assertArrayEquals(signature(probe.answer(probe.cold())), signature(probe.answer(probe.warmed)));
    }

    private static CollisionHelper.ScanCache capture(Level level, LevelDBChunk chunk, int sectionY) {
        var cache = new CollisionHelper.ScanCache();
        cache.begin(level);
        stamp(cache, chunk, sectionY);
        cache.commit(SCAN, BOX);
        return cache;
    }

    private static void stamp(CollisionHelper.ScanCache cache, LevelDBChunk chunk, int sectionY) {
        ChunkSection section = chunk.getSection(sectionY);
        int version = section instanceof LevelDBChunkSection standard
                ? standard.getBlockVersion(0) : CollisionHelper.ScanCache.NO_VERSION;
        cache.stamp(chunk.getX(), chunk.getZ(), sectionY, chunk, section, version);
    }

    private static void setSections(LevelDBChunk chunk, ChunkSection[] sections) throws Exception {
        Field field = BaseChunk.class.getDeclaredField("sections");
        field.setAccessible(true);
        field.set(chunk, sections);
    }

    private static String[] signature(Block[] blocks) {
        return Arrays.stream(blocks).map(b -> b.getClass().getName() + ':' + b.getId() + ':' + b.getDamage()
                + '@' + b.getFloorX() + ',' + b.getFloorY() + ',' + b.getFloorZ()).toArray(String[]::new);
    }

    private static void set(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class OffsetFixture {
        final CollisionCandidatesTest.World world = CollisionCandidatesTest.World.flat(BlockID.STONE, 63);
        final LevelDBChunk chunk;
        OffsetFixture(DimensionData dimension) {
            when(world.level.getDimensionData()).thenReturn(dimension);
            chunk = new LevelDBChunk(world.provider, 0, 0, new ChunkSection[dimension.getHeight() >> 4],
                    null, null, null, null, null, ChunkState.FINISHED);
        }
    }

    private static class FixedCustomDimension extends DimensionData {
        FixedCustomDimension() { super(0, 0, 255); }
        @Override public int getSectionOffset() { return 0; }
    }

    private static final class MutableCustomDimension extends DimensionData {
        int offset;
        MutableCustomDimension() { super(0, 0, 255); }
        @Override public int getSectionOffset() { return offset; }
    }

    private static final class MutableSectionOffset extends DimensionData {
        int offset;
        MutableSectionOffset() { super(0, 0, 255); }
        @Override public int getSectionOffset() { return offset; }
    }

    private static final class MutableMinSectionY extends DimensionData {
        int minSectionY;
        MutableMinSectionY() { super(0, 0, 255); }
        @Override public int getMinSectionY() { return minSectionY; }
    }

    private static final class OffsetProbe {
        final CollisionCandidatesTest.World world = CollisionCandidatesTest.World.flat(BlockID.STONE, 63);
        final DimensionData dimension;
        final LevelDBChunk chunk;
        final CollisionHelper warmed;
        final LevelDBChunkSection first = section(4, BlockID.FIRE);
        final LevelDBChunkSection second = section(5, BlockID.STONE);

        OffsetProbe(DimensionData dimension) throws Exception {
            this.dimension = dimension;
            assertEquals(Level.class, world.level.getClass());
            assertEquals(LevelDBProvider.class, world.provider.getClass());
            set(Level.class, world.level, "provider", world.provider);
            set(Level.class, world.level, "dimensionData", dimension);
            set(LevelDBProvider.class, world.provider, "level", world.level);
            doCallRealMethod().when(world.level).getProvider();
            doCallRealMethod().when(world.level).requireProvider();
            doCallRealMethod().when(world.level).getMinBlockY();
            doCallRealMethod().when(world.level).getMaxBlockY();
            doCallRealMethod().when(world.level).getDimensionData();
            doCallRealMethod().when(world.provider).getLevel();
            doCallRealMethod().when(world.provider).getMinBlockY();
            doCallRealMethod().when(world.provider).getMaxBlockY();
            ChunkSection[] sections = new ChunkSection[16];
            sections[4] = first;
            sections[5] = second;
            chunk = new LevelDBChunk(world.provider, 0, 0, sections,
                    null, null, null, null, null, ChunkState.FINISHED);
            assertEquals(LevelDBChunk.class, chunk.getClass());
            assertSame(first, chunk.getSection(4));
            assertNotSame(first, second);
            assertEquals(first.getBlockVersion(0), second.getBlockVersion(0));
            world.chunks[3][3] = chunk;
            warmed = cold();
        }

        void warm() {
            assertEquals(0, chunk.getSectionOffset());
            Block[] initial = answer(warmed);
            assertEquals(1, initial.length);
            assertEquals(BlockID.FIRE, initial[0].getId());
        }

        void changed(String getter) {
            assertSame(dimension, world.level.getDimensionData(), "dimension identity did not change");
            assertEquals(0, world.level.getMinBlockY());
            assertEquals(255, world.level.getMaxBlockY());
            assertEquals(1, chunk.getSectionOffset());
            assertSame(second, chunk.getSection(4), "native getter resolves the shifted slot");
            Block[] warm = answer(warmed);
            Block[] cold = answer(cold());
            assertEquals(1, cold.length);
            assertEquals(BlockID.STONE, cold[0].getId());
            System.out.println(getter + " offset=0->1 world=0..255 sameDimension=true sameSectionVersion=true"
                    + " warm=" + Arrays.toString(signature(warm)) + " cold=" + Arrays.toString(signature(cold)));
            assertArrayEquals(signature(cold), signature(warm), "mutable dimension offset must remain authoritative");
        }

        Block[] answer(CollisionHelper helper) {
            return CollisionHelper.filterCollisionBlocks(helper.getCollisionCandidates(SCAN, BOX), BOX, .5, .5, .5);
        }

        CollisionHelper cold() { return world.helperAt(5.5, 5.5); }
    }

    private static LevelDBChunkSection section(int sectionY, int id) {
        StateBlockStorage storage = new StateBlockStorage();
        storage.set(5, 0, 5, BlockStateSnapshot.builder().legacyId(id).legacyData(0).build());
        return new LevelDBChunkSection(sectionY, new StateBlockStorage[]{storage}, false);
    }
}
