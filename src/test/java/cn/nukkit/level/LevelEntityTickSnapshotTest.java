package cn.nukkit.level;

import cn.nukkit.MockServer;
import cn.nukkit.entity.Entity;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.scheduler.BlockUpdateScheduler;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

/** Callback order and admission remain those of a key snapshot, not a live entity iterator. */
class LevelEntityTickSnapshotTest {
    @Test void newEntityWaitsForTheNextTick() throws Exception {
        Fixture f = new Fixture();
        f.add(1, tick -> { if (tick == 10) f.add(3, t -> true); return true; });
        f.add(2, tick -> true);
        f.level.doTick(10);
        assertEquals(List.of("1:10", "2:10"), f.calls);
        f.calls.clear();
        f.level.doTick(11);
        assertEquals(List.of("1:11", "2:11", "3:11"), f.calls);
    }

    @Test void removalAndReinsertionOfLaterKeyUsesTheNewEntityThisTick() throws Exception {
        Fixture f = new Fixture();
        f.add(1, tick -> { f.level.updateEntities.remove(4); return true; });
        f.add(2, tick -> { f.add(4, t -> true); return true; });
        f.add(4, tick -> fail("removed instance must not update"));
        f.level.doTick(10);
        assertEquals(List.of("1:10", "2:10", "4:10"), f.calls);
    }

    @Test void replacementBeforeVisitUsesFreshLookup() throws Exception {
        Fixture f = new Fixture();
        f.add(1, tick -> { f.add(2, t -> true); return true; });
        f.add(2, tick -> fail("replaced instance must not update"));
        f.level.doTick(10);
        assertEquals(List.of("1:10", "2:10"), f.calls);
    }

    @Test void closedAndFalseUpdatesAreRemovedButSleepingEntitiesStayScheduled() throws Exception {
        Fixture f = new Fixture();
        f.add(1, tick -> false);
        CountingEntity closed = f.add(2, tick -> fail("closed entity must not update"));
        closed.closed = true;
        CountingEntity asleep = f.add(3, tick -> fail("sleeping entity must not update"));
        asleep.sleep = true;
        f.level.doTick(10);
        assertEquals(List.of("1:10"), f.calls);
        assertEquals(1, f.level.updateEntities.size());
        assertSame(asleep, f.level.updateEntities.get(3));
        asleep.sleep = false;
        asleep.action = tick -> true;
        f.level.doTick(11);
        assertEquals(List.of("1:10", "3:11"), f.calls);
    }

    @Test void reentrantTickCannotOverwriteOuterKeys() throws Exception {
        Fixture f = new Fixture();
        f.add(1, tick -> {
            if (tick == 10) { f.add(3, t -> true); f.level.doTick(20); }
            return true;
        });
        f.add(2, tick -> true);
        f.level.doTick(10);
        assertEquals(List.of("1:10", "1:20", "2:20", "3:20", "2:10"), f.calls);
    }

    @Test void exceptionReturnsBufferAndNextTickUsesNewSnapshot() throws Exception {
        Fixture f = new Fixture();
        CountingEntity first = f.add(1, tick -> { throw new IllegalStateException("callback failed"); });
        f.add(2, tick -> true);
        assertThrows(IllegalStateException.class, () -> f.level.doTick(10));
        first.action = tick -> true;
        f.level.updateEntities.remove(2);
        f.add(4, tick -> true);
        f.calls.clear();
        f.level.doTick(11);
        assertEquals(List.of("1:11", "4:11"), f.calls);
    }

    @Test void shorterSnapshotsDoNotTickRetainedTail() throws Exception {
        Fixture f = new Fixture();
        for (int id = 1; id <= 128; id++) f.add(id, tick -> true);
        f.level.doTick(10);
        f.level.updateEntities.clear();
        f.add(9, tick -> true);
        f.calls.clear();
        f.level.doTick(11);
        assertEquals(List.of("9:11"), f.calls);
    }

    @Test void zeroKeyRemainsARealEntityKey() throws Exception {
        Fixture f = new Fixture();
        f.add(0, tick -> true);
        f.add(1, tick -> true);
        f.level.doTick(10);
        assertEquals(List.of("0:10", "1:10"), f.calls);
    }

    @Test void concurrentShrinkPaddingCannotReuseOldTailKeys() throws Exception {
        Fixture f = new Fixture();
        ShrinkingMap map = new ShrinkingMap();
        Field field = Level.class.getDeclaredField("updateEntities"); field.setAccessible(true);
        field.set(f.level, map);
        f.add(1, tick -> true); f.add(2, tick -> true); f.add(4, tick -> true);
        f.level.doTick(10); // fill cached buffer
        f.calls.clear();
        map.shrink = true;
        f.level.doTick(11);
        assertEquals(List.of("1:11"), f.calls);
    }

    static class ShrinkingMap extends Long2ObjectNonBlockingMap<Entity> {
        boolean shrink;
        @Override public int size() {
            int before = super.size();
            if (shrink) { shrink = false; remove(2); remove(4); }
            return before;
        }
        @Override public boolean isEmpty() { return super.size() == 0; }
    }

    static final class Fixture {
        final List<String> calls = new ArrayList<>();
        final Level level;
        Fixture() throws Exception {
            MockServer.init();
            level = mock(Level.class, CALLS_REAL_METHODS);
            set(level, "server", MockServer.get());
            set(level, "provider", mock(LevelProvider.class));
            set(level, "updateEntities", new Long2ObjectNonBlockingMap<Entity>());
            set(level, "players", new PlayerRangeMap());
            set(level, "entities", new Long2ObjectNonBlockingMap<Entity>());
            set(level, "chunkTickList", new Long2IntOpenHashMap());
            set(level, "changedBlocks", new Long2ObjectOpenHashMap<>());
            set(level, "dimensionData", DimensionData.LEGACY_DIMENSION);
            set(level, "lightQueue", new ConcurrentHashMap<>());
            set(level, "updateQueue", new BlockUpdateScheduler(level, 0));
            set(level, "normalUpdateQueue", new ConcurrentLinkedQueue<>());
            set(level, "normalUpdateQueueSize", new AtomicInteger());
            set(level, "updateBlockEntities", new ConcurrentLinkedQueue<>());
            set(level, "chunkPackets", new ConcurrentHashMap<>());
            set(level, "chunkSendQueues", new Object2ObjectOpenHashMap<>());
            set(level, "pendingChunkRequests", new Object2ObjectOpenHashMap<>());
            set(level, "asyncChunkRequestFailedQueue", new ConcurrentLinkedQueue<>());
            set(level, "completedChunkLoads", new ConcurrentLinkedQueue<>());
            level.gameRules = GameRules.getDefault();
            level.gameRules.setGameRule(GameRule.DO_WEATHER_CYCLE, false); level.gameRules.refresh();

        }
        CountingEntity add(long id, IntPredicate action) {
            try {
                CountingEntity entity = allocate(CountingEntity.class);
                entity.key = id; entity.calls = calls; entity.action = action;
                level.updateEntities.put(id, entity);
                return entity;
            } catch (Exception e) { throw new AssertionError(e); }
        }
    }

    private static void set(Level level, String name, Object value) throws Exception {
        Field f = Level.class.getDeclaredField(name); f.setAccessible(true); f.set(level, value);
    }
    static class CountingEntity extends Entity {
        long key;
        List<String> calls;
        IntPredicate action;
        boolean sleep;
        CountingEntity() { super(null, null); }
        @Override public int getNetworkId() { return -1; }
        @Override public boolean isActivationThrottled(int tick) { return sleep; }
        @Override public boolean onUpdate(int tick) { calls.add(key + ":" + tick); return action.test(tick); }
    }

    @SuppressWarnings("removal")
    static <T> T allocate(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }
}
