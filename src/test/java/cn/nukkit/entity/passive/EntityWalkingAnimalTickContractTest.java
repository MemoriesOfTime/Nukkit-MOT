package cn.nukkit.entity.passive;

import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.objenesis.ObjenesisStd;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EntityWalkingAnimalTickContractTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void forwardsOnlyTheExactNonNullMovementTarget(boolean hasTarget) {
        Harness actor = actor();
        Vector3 target = hasTarget ? new Vector3(7, 64, 8) : null;
        actor.result = target;

        assertTrue(actor.onUpdate(11));

        assertEquals(hasTarget ? List.of("alive", "base", "move", "look")
                : List.of("alive", "base", "move"), actor.calls);
        assertEquals(1, actor.baseDiff);
        assertEquals(1, actor.moveDiff);
        assertEquals(11, actor.updateSeenByBase);
        assertSame(target, actor.lookedAt);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -3, 17})
    void preservesUnclampedTickDifferenceAndPublishesLastUpdateBeforeBase(int delta) {
        Harness actor = actor();

        assertTrue(actor.onUpdate(10 + delta));

        assertEquals(delta, actor.baseDiff);
        assertEquals(delta, actor.moveDiff);
        assertEquals(10 + delta, actor.updateSeenByBase);
        assertEquals(10 + delta, actor.lastUpdate);
        assertEquals(List.of("alive", "base", "move"), actor.calls);
    }

    @Test
    void closedActorReturnsBeforeAnyHook() {
        Harness actor = actor();
        actor.closed = true;

        assertFalse(actor.onUpdate(11));

        assertTrue(actor.calls.isEmpty());
        assertEquals(10, actor.lastUpdate);
        assertEquals(0, actor.deadTicks);
    }

    @ParameterizedTest
    @ValueSource(ints = {21, 22})
    void deadActorOnlyAdvancesItsCloseCounter(int initialDeadTicks) {
        Harness actor = actor();
        actor.alive = false;
        actor.deadTicks = initialDeadTicks;

        assertEquals(initialDeadTicks < 22, actor.onUpdate(11));

        assertEquals(initialDeadTicks + 1, actor.deadTicks);
        assertEquals(initialDeadTicks < 22 ? List.of("alive") : List.of("alive", "close"), actor.calls);
        assertEquals(10, actor.lastUpdate);
    }

    @ParameterizedTest
    @EnumSource(Stage.class)
    void propagatesTheSameHookFailureWithoutLaterCalls(Stage stage) {
        Harness actor = actor();
        actor.result = new Vector3(7, 64, 8);
        actor.failureAt = stage;
        actor.failure = new IllegalStateException("fixture failure");

        assertSame(actor.failure, assertThrows(IllegalStateException.class, () -> actor.onUpdate(11)));

        assertEquals(switch (stage) {
            case BASE -> List.of("alive", "base");
            case MOVE -> List.of("alive", "base", "move");
            case LOOK -> List.of("alive", "base", "move", "look");
        }, actor.calls);
        assertEquals(11, actor.lastUpdate);
        if (stage == Stage.LOOK) {
            assertSame(actor.result, actor.lookedAt);
        }
    }

    private static Harness actor() {
        Harness actor = new ObjenesisStd().newInstance(Harness.class);
        actor.calls = new ArrayList<>();
        actor.alive = true;
        actor.lastUpdate = 10;
        return actor;
    }

    private enum Stage { BASE, MOVE, LOOK }

    /** Only the surrounding hooks are controlled; the inherited onUpdate body is real. */
    private static final class Harness extends EntityCow {
        List<String> calls;
        boolean alive;
        int baseDiff;
        int moveDiff;
        int updateSeenByBase;
        Vector3 result;
        Vector3 lookedAt;
        Stage failureAt;
        IllegalStateException failure;

        private Harness() {
            super(null, null);
        }

        @Override
        public boolean isAlive() {
            calls.add("alive");
            return alive;
        }

        @Override
        public boolean entityBaseTick(int diff) {
            calls.add("base");
            baseDiff = diff;
            updateSeenByBase = lastUpdate;
            failAt(Stage.BASE);
            return false;
        }

        @Override
        public Vector3 updateMove(int diff) {
            calls.add("move");
            moveDiff = diff;
            failAt(Stage.MOVE);
            return result;
        }

        @Override
        public void lookAt(Vector3 target) {
            calls.add("look");
            lookedAt = target;
            failAt(Stage.LOOK);
        }

        @Override
        public void close() {
            calls.add("close");
            closed = true;
        }

        private void failAt(Stage stage) {
            if (failureAt == stage) {
                throw failure;
            }
        }
    }
}
