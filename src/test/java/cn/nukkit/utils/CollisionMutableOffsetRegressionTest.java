package cn.nukkit.utils;

import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.DimensionData;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.ChunkSection;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import cn.nukkit.level.format.leveldb.structure.BlockStateSnapshot;
import cn.nukkit.level.format.leveldb.structure.ChunkState;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunkSection;
import cn.nukkit.level.format.leveldb.structure.StateBlockStorage;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.SimpleAxisAlignedBB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CollisionMutableOffsetRegressionTest {
    private static final AxisAlignedBB BOX = new SimpleAxisAlignedBB(5, 64, 5, 6, 65.6, 6);
    private static final AxisAlignedBB SCAN = BOX.grow(.5, .5, .5);

    @BeforeAll static void init() { MockServer.init(); }

    @Test void overridingMutableSectionOffsetMustInvalidateWarmCandidates() throws Exception {
        var dimension = new MutableSectionOffset();
        Probe probe = new Probe(dimension);
        probe.warm();
        dimension.offset = 1;
        probe.changed("getSectionOffset");
    }

    @Test void overridingMutableMinSectionYMustInvalidateWarmCandidates() throws Exception {
        var dimension = new MutableMinSectionY();
        Probe probe = new Probe(dimension);
        probe.warm();
        dimension.minSectionY = -1;
        probe.changed("getMinSectionY");
    }

    @Test void unchangedCustomDimensionKeepsWarmAndColdAnswersEqual() throws Exception {
        Probe probe = new Probe(new MutableSectionOffset());
        probe.warm();
        assertArrayEquals(signature(probe.answer(probe.cold())), signature(probe.answer(probe.warmed)));
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

    private static final class Probe {
        final CollisionCandidatesTest.World world = CollisionCandidatesTest.World.flat(BlockID.STONE, 63);
        final DimensionData dimension;
        final LevelDBChunk chunk;
        final CollisionHelper warmed;
        final LevelDBChunkSection first = section(4, BlockID.FIRE);
        final LevelDBChunkSection second = section(5, BlockID.STONE);

        Probe(DimensionData dimension) throws Exception {
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

    private static void set(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static String[] signature(Block[] blocks) {
        return Arrays.stream(blocks).map(b -> b.getClass().getName() + ':' + b.getId() + ':' + b.getDamage()
                + '@' + b.getFloorX() + ',' + b.getFloorY() + ',' + b.getFloorZ()).toArray(String[]::new);
    }
}
