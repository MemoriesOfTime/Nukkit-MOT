package cn.nukkit.utils;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.level.Level;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.SimpleAxisAlignedBB;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for main-thread freezes from malformed entity AABBs feeding {@link CollisionHelper}
 * runaway loops (cf. commit c446b28b).
 * Verifies pathological AABBs (oversized/NaN/Infinity) are rejected cheaply and emit a diagnostic warning.
 *
 * @author LT_Name
 */
public class CollisionHelperRunawayTest {

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
