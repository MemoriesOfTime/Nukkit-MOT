package cn.nukkit.utils;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockStone;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.SimpleAxisAlignedBB;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CollisionOwnedResultContractTest {
    private final SimpleAxisAlignedBB bounds = new SimpleAxisAlignedBB(0, 0, 0, 1, 1, 1);

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 8, 9})
    void ownedCompactionPreservesOrderAndVisitsEveryPredicateOnce(int size) throws Exception {
        for (int mode = 0; mode < 3; mode++) {
            List<String> trace = new ArrayList<>();
            List<Block> expected = new ArrayList<>();
            Block[] blocks = new Block[size];
            for (int i = 0; i < size; i++) {
                boolean accepted = mode == 0 || (mode == 1 && i % 2 == 0);
                Collider block = new Collider("b" + i, i % 3 != 0, i % 3 == 1, accepted, trace);
                blocks[i] = block;
                if (accepted) expected.add(block);
            }
            List<String> expectedTrace = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                expectedTrace.add("b" + i + ":pass");
                if (i % 3 != 0) expectedTrace.add("b" + i + ":dynamic");
                expectedTrace.add("b" + i + ":collide");
            }
            Block[] result = owned(blocks, bounds);
            assertArrayEquals(expected.toArray(Block[]::new), result);
            assertEquals(expectedTrace, trace);
            for (int i = 0; i < expected.size(); i++) assertSame(expected.get(i), result[i]);
            if (expected.isEmpty()) assertSame(Block.EMPTY_ARRAY, result);
        }
    }

    @Test
    void retainedBoundsAndLiveChangesKeepOriginalEvaluationOrder() throws Exception {
        List<String> trace = new ArrayList<>();
        Collider dynamic = new Collider("dynamic", true, true, true, trace);
        Collider shrink = new Collider("shrink", true, false, true, trace);
        Collider solid = new Collider("solid", false, false, true, trace);
        dynamic.action = () -> bounds.setBounds(0, 0, 0, 4, 1, 4);
        assertArrayEquals(new Block[]{dynamic, shrink, solid},
                owned(new Block[]{dynamic, shrink, solid}, bounds));
        assertEquals(-0.5, dynamic.checked.getMinX());
        assertEquals(1.5, dynamic.checked.getMaxX());
        assertEquals(1, shrink.checked.getMinX());
        assertEquals(3, shrink.checked.getMaxX());
        assertNotSame(dynamic.checked, shrink.checked);
        assertSame(bounds, solid.checked);
        assertEquals(List.of("dynamic:pass", "dynamic:dynamic", "dynamic:collide",
                "shrink:pass", "shrink:dynamic", "shrink:collide", "solid:pass", "solid:collide"), trace);
    }

    @Test
    void nestedQueryAndMutationOfLaterBlockRemainLive() throws Exception {
        List<String> trace = new ArrayList<>();
        Collider first = new Collider("first", false, false, true, trace);
        Collider later = new Collider("later", false, false, true, trace);
        Collider last = new Collider("last", false, false, true, trace);
        Collider nested = new Collider("nested", false, false, true, trace);
        Block[][] retainedNested = new Block[1][];
        first.action = () -> {
            later.accepted = false;
            try { retainedNested[0] = owned(new Block[]{nested}, bounds); }
            catch (Exception exception) { throw new AssertionError(exception); }
        };
        Block[] result = owned(new Block[]{first, later, last}, bounds);
        assertArrayEquals(new Block[]{first, last}, result);
        assertArrayEquals(new Block[]{nested}, retainedNested[0]);
        result[0] = null;
        assertSame(nested, retainedNested[0][0]);
        assertEquals(List.of("first:pass", "first:collide", "nested:pass", "nested:collide",
                "later:pass", "later:collide", "last:pass", "last:collide"), trace);
    }

    @Test
    void callbackFailureStopsAtSameBlockAndDoesNotPoisonNextQuery() throws Exception {
        List<String> trace = new ArrayList<>();
        Collider first = new Collider("first", false, false, true, trace);
        Collider failure = new Collider("failure", false, false, true, trace);
        Collider last = new Collider("last", false, false, true, trace);
        IllegalStateException expected = new IllegalStateException("collision callback");
        failure.action = () -> { throw expected; };
        assertSame(expected, assertThrows(IllegalStateException.class,
                () -> owned(new Block[]{first, failure, last}, bounds)));
        assertEquals(List.of("first:pass", "first:collide", "failure:pass", "failure:collide"), trace);
        failure.action = null;
        assertArrayEquals(new Block[]{first, failure, last},
                owned(new Block[]{first, failure, last}, bounds));
    }

    private static Block[] owned(Block[] blocks, AxisAlignedBB bounds) throws Exception {
        var method = CollisionHelper.class.getDeclaredMethod("filterCollisionBlocks", Block[].class,
                AxisAlignedBB.class, double.class, double.class, double.class, boolean.class);
        method.setAccessible(true);
        try { return (Block[]) method.invoke(null, blocks, bounds, 0.5, 0.5, 0.5, true); }
        catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof RuntimeException runtime) throw runtime;
            if (exception.getCause() instanceof Error error) throw error;
            throw exception;
        }
    }

    private static final class Collider extends BlockStone {
        private final String label;
        private final boolean pass;
        private final boolean dynamic;
        private final List<String> trace;
        private boolean accepted;
        private Runnable action;
        private AxisAlignedBB checked;
        Collider(String label, boolean pass, boolean dynamic, boolean accepted, List<String> trace) {
            this.label = label; this.pass = pass; this.dynamic = dynamic;
            this.accepted = accepted; this.trace = trace;
        }
        @Override public boolean canPassThrough() { trace.add(label + ":pass"); return pass; }
        @Override public boolean hasDynamicCollision() { trace.add(label + ":dynamic"); return dynamic; }
        @Override public boolean collidesWithBB(AxisAlignedBB box, boolean collisionBB) {
            trace.add(label + ":collide");
            assertTrue(collisionBB);
            checked = box;
            if (action != null) action.run();
            return accepted;
        }
    }
}
