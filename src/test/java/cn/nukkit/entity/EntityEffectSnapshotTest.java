package cn.nukkit.entity;

import cn.nukkit.event.entity.EntityPotionEffectEvent;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.anvil.Anvil;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.potion.Effect;
import org.junit.jupiter.api.Test;
import org.objenesis.ObjenesisStd;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Callback witnesses are excluded from the separate native benchmark JVM. */
class EntityEffectSnapshotTest {
    private enum Stage { CAN_TICK, APPLY, GET_DURATION, SET_DURATION, ID, REMOVE }

    private static final class WitnessEffect extends Effect {
        private final String label;
        private final List<String> trace;
        private final Map<Stage, Runnable> once = new EnumMap<>(Stage.class);

        private WitnessEffect(int id, String label, List<String> trace) {
            super(id, "snapshot-test", 0, 0, 0);
            this.label = label;
            this.trace = trace;
            this.duration = 100;
        }

        private int remaining() { return duration; }
        private void initialDuration(int ticks) { duration = ticks; }

        private void call(Stage stage) {
            Runnable callback = once.remove(stage);
            if (callback != null) callback.run();
        }

        @Override public boolean canTick() {
            trace.add(label + ".can");
            call(Stage.CAN_TICK);
            return true;
        }

        @Override public void applyEffect(Entity entity) {
            trace.add(label + ".apply");
            call(Stage.APPLY);
        }

        @Override public int getDuration() {
            trace.add(label + ".get:" + duration);
            call(Stage.GET_DURATION);
            return duration;
        }

        @Override public Effect setDuration(int ticks) {
            trace.add(label + ".set:" + ticks);
            call(Stage.SET_DURATION);
            duration = ticks;
            return this;
        }

        @Override public int getId() {
            trace.add(label + ".id");
            call(Stage.ID);
            return id;
        }
    }

    /** Concrete environment callbacks record removal/close without a running server. */
    private static final class FixtureEntity extends Entity {
        private List<String> trace;
        private Runnable removal;
        private FixtureEntity() { super(null, new CompoundTag()); }
        @Override public int getNetworkId() { return -1; }
        @Override public void removeEffect(int id, EntityPotionEffectEvent.Cause cause) {
            trace.add("remove:" + id + ":" + cause);
            Runnable callback = removal;
            removal = null;
            if (callback != null) callback.run();
            getEffects().remove(id);
        }
        @Override public void close() {
            trace.add("close");
            closed = true;
            getEffects().clear();
        }
    }

    private static void field(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static FixtureEntity entity(List<String> trace) throws Exception {
        ObjenesisStd allocator = new ObjenesisStd(true);
        FixtureEntity entity = allocator.newInstance(FixtureEntity.class);
        Level level = allocator.newInstance(Level.class);
        field(Level.class, level, "provider", allocator.newInstance(Anvil.class));
        field(Entity.class, entity, "effects", new ConcurrentHashMap<Integer, Effect>());
        field(Entity.class, entity, "passengers", new ArrayList<Entity>());
        field(Entity.class, entity, "health", 20f);
        entity.level = level;
        entity.y = 64;
        entity.noClip = true;
        entity.trace = trace;
        return entity;
    }

    private static List<String> steps(String label, int before, int after) {
        return List.of(label + ".can", label + ".apply", label + ".get:" + before,
                label + ".set:" + after, label + ".get:" + after);
    }

    @SafeVarargs
    private static List<String> concat(List<String>... lists) {
        List<String> result = new ArrayList<>();
        for (List<String> list : lists) result.addAll(list);
        return result;
    }

    /** Baseline has no cache: these implementation ownership checks apply only to the candidate. */
    private static Object assertLeaseEmpty() throws Exception {
        Field local;
        try {
            local = Entity.class.getDeclaredField("EFFECT_TICK_SNAPSHOT");
        } catch (NoSuchFieldException baseline) {
            return null;
        }
        assertTrue(Modifier.isPrivate(local.getModifiers()));
        assertTrue(Modifier.isStatic(local.getModifiers()));
        local.setAccessible(true);
        Object lease = ((ThreadLocal<?>) local.get(null)).get();
        Field array = lease.getClass().getDeclaredField("effects");
        Field busy = lease.getClass().getDeclaredField("inUse");
        array.setAccessible(true);
        busy.setAccessible(true);
        Effect[] values = (Effect[]) array.get(lease);
        assertTrue(values.length > 0 && values.length <= 64);
        assertFalse(busy.getBoolean(lease));
        assertTrue(Arrays.stream(values).allMatch(value -> value == null));
        assertTrue(Arrays.stream(Entity.class.getDeclaredFields())
                .noneMatch(f -> f.getType() == lease.getClass() && !Modifier.isStatic(f.getModifiers())));
        return lease;
    }

    @Test
    void retainsRemovedAndReplacedReferencesAndExcludesNewEffects() throws Exception {
        List<String> trace = new ArrayList<>();
        FixtureEntity entity = entity(trace);
        WitnessEffect a = new WitnessEffect(1, "A", trace);
        WitnessEffect b = new WitnessEffect(2, "B", trace);
        WitnessEffect replacement = new WitnessEffect(2, "replacement", trace);
        WitnessEffect added = new WitnessEffect(3, "added", trace);
        entity.getEffects().put(1, a);
        entity.getEffects().put(2, b);
        a.once.put(Stage.APPLY, () -> {
            entity.getEffects().remove(2);
            entity.getEffects().put(2, replacement);
            entity.getEffects().put(3, added);
        });
        assertFalse(entity.entityBaseTick(1));
        assertEquals(concat(steps("A", 100, 99), steps("B", 100, 99)), trace);
        assertEquals(99, b.remaining());
        assertEquals(100, replacement.remaining());
        assertEquals(100, added.remaining());
        assertSame(replacement, entity.getEffects().get(2));
        assertLeaseEmpty();
    }

    @Test
    void duplicateReferencesStillExecuteOncePerCapturedMapEntry() throws Exception {
        List<String> trace = new ArrayList<>();
        FixtureEntity entity = entity(trace);
        WitnessEffect effect = new WitnessEffect(1, "A", trace);
        entity.getEffects().put(1, effect);
        entity.getEffects().put(2, effect);
        entity.entityBaseTick(1);
        assertEquals(concat(steps("A", 100, 99), steps("A", 99, 98)), trace);
        assertEquals(98, effect.remaining());
        assertLeaseEmpty();
    }

    @Test
    void sameEntityReentryRetainsTheOuterSnapshot() throws Exception {
        List<String> trace = new ArrayList<>();
        FixtureEntity entity = entity(trace);
        WitnessEffect a = new WitnessEffect(1, "A", trace);
        WitnessEffect b = new WitnessEffect(2, "B", trace);
        entity.getEffects().put(1, a);
        entity.getEffects().put(2, b);
        a.once.put(Stage.APPLY, () -> entity.entityBaseTick(1));
        entity.entityBaseTick(1);
        assertEquals(concat(List.of("A.can", "A.apply"), steps("A", 100, 99),
                steps("B", 100, 99), List.of("A.get:99", "A.set:98", "A.get:98"),
                steps("B", 99, 98)), trace);
        assertEquals(2, entity.age);
        assertEquals(98, a.remaining());
        assertEquals(98, b.remaining());
        assertLeaseEmpty();
    }

    @Test
    void crossEntityReentryUsesAnIndependentSnapshot() throws Exception {
        List<String> trace = new ArrayList<>();
        FixtureEntity outer = entity(trace), inner = entity(trace);
        WitnessEffect a = new WitnessEffect(1, "A", trace);
        WitnessEffect b = new WitnessEffect(2, "B", trace);
        WitnessEffect x = new WitnessEffect(1, "X", trace);
        outer.getEffects().put(1, a);
        outer.getEffects().put(2, b);
        inner.getEffects().put(1, x);
        a.once.put(Stage.APPLY, () -> inner.entityBaseTick(1));
        outer.entityBaseTick(1);
        assertEquals(concat(List.of("A.can", "A.apply"), steps("X", 100, 99),
                List.of("A.get:100", "A.set:99", "A.get:99"), steps("B", 100, 99)), trace);
        assertEquals(1, outer.age);
        assertEquals(1, inner.age);
        assertLeaseEmpty();
    }

    @Test
    void nestedFailureDoesNotReleaseTheOuterLeaseBeforeAnotherNestedCall() throws Exception {
        List<String> trace = new ArrayList<>();
        FixtureEntity outer = entity(trace), inner = entity(trace);
        WitnessEffect a = new WitnessEffect(1, "A", trace);
        WitnessEffect b = new WitnessEffect(2, "B", trace);
        WitnessEffect x = new WitnessEffect(1, "X", trace);
        outer.getEffects().put(1, a);
        outer.getEffects().put(2, b);
        inner.getEffects().put(1, x);
        RuntimeException failure = new RuntimeException("nested witness");
        b.once.put(Stage.CAN_TICK, () -> { throw failure; });
        a.once.put(Stage.APPLY, () -> {
            assertSame(failure, assertThrows(RuntimeException.class, () -> outer.entityBaseTick(1)));
            inner.entityBaseTick(1);
        });
        outer.entityBaseTick(1);
        assertEquals(98, a.remaining());
        assertEquals(99, b.remaining());
        assertEquals(99, x.remaining());
        assertEquals(1, outer.age);
        assertEquals(1, inner.age);
        assertEquals(2, Collections.frequency(trace, "B.can"));
        assertEquals(1, Collections.frequency(trace, "B.apply"));
        assertLeaseEmpty();
    }

    @Test
    void everyCallbackFailurePreservesExceptionAndAllowsRecovery() throws Exception {
        for (Stage stage : Stage.values()) {
            List<String> trace = new ArrayList<>();
            FixtureEntity entity = entity(trace);
            WitnessEffect a = new WitnessEffect(1, "A", trace);
            WitnessEffect b = new WitnessEffect(2, "B", trace);
            entity.getEffects().put(1, a);
            entity.getEffects().put(2, b);
            if (stage == Stage.ID || stage == Stage.REMOVE) a.initialDuration(1);
            RuntimeException failure = new RuntimeException(stage.name());
            Runnable action = () -> { throw failure; };
            if (stage == Stage.REMOVE) entity.removal = action;
            else a.once.put(stage, action);
            assertSame(failure, assertThrows(RuntimeException.class, () -> entity.entityBaseTick(1)), stage.name());
            assertEquals(0, entity.age);
            assertEquals(100, b.remaining());
            assertLeaseEmpty();
            entity.entityBaseTick(1);
            assertEquals(1, entity.age);
            assertEquals(99, b.remaining());
            assertLeaseEmpty();
        }
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void failedRawMapCaptureInvokesNoEffectAndDoesNotRetainPartialReferences() throws Exception {
        List<String> trace = new ArrayList<>();
        FixtureEntity entity = entity(trace);
        WitnessEffect a = new WitnessEffect(1, "A", trace);
        entity.getEffects().put(1, a);
        ((Map) entity.getEffects()).put(2, "wrong value type");
        assertThrows(ArrayStoreException.class, () -> entity.entityBaseTick(1));
        assertTrue(trace.isEmpty());
        assertEquals(100, a.remaining());
        assertEquals(0, entity.age);
        assertLeaseEmpty();
        entity.getEffects().remove(2);
        entity.entityBaseTick(1);
        assertEquals(steps("A", 100, 99), trace);
        assertLeaseEmpty();
    }

    @Test
    void expirationAndCloseCallbacksRetainTheRemainingCapturedReferences() throws Exception {
        List<String> trace = new ArrayList<>();
        FixtureEntity entity = entity(trace);
        WitnessEffect a = new WitnessEffect(1, "A", trace);
        WitnessEffect b = new WitnessEffect(2, "B", trace);
        a.initialDuration(1);
        entity.getEffects().put(1, a);
        entity.getEffects().put(2, b);
        entity.removal = entity::close;
        entity.entityBaseTick(1);
        assertEquals(concat(steps("A", 1, 0), List.of("A.id", "remove:1:EXPIRATION", "close"),
                steps("B", 100, 99)), trace);
        assertTrue(entity.closed);
        assertTrue(entity.getEffects().isEmpty());
        assertEquals(1, entity.age);
        assertLeaseEmpty();
    }

    @Test
    void emptySmallAndOversizedMapsKeepEveryDurationAcrossRepeatedTicks() throws Exception {
        for (int count : new int[]{0, 1, 16, 17, 64, 128, 1}) {
            FixtureEntity entity = entity(new ArrayList<>());
            List<Effect> effects = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Effect effect = new Effect(Effect.SPEED, "native", 0, 0, 0).setDuration(100);
                entity.getEffects().put(i, effect);
                effects.add(effect);
            }
            entity.entityBaseTick(1);
            entity.entityBaseTick(3);
            assertEquals(4, entity.age);
            assertEquals(count, entity.getEffects().size());
            for (Effect effect : effects) assertEquals(96, effect.getDuration());
            if (count != 0) assertLeaseEmpty();
        }
    }

    @Test
    void parallelThreadsUseIndependentBuffersAndReleaseTheirReferences() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CyclicBarrier barrier = new CyclicBarrier(4);
        Set<Object> leases = Collections.newSetFromMap(new ConcurrentHashMap<>());
        List<Future<Void>> futures = new ArrayList<>();
        try {
            for (int n = 0; n < 4; n++) {
                int index = n;
                futures.add(executor.submit(() -> {
                    List<String> trace = new ArrayList<>();
                    FixtureEntity entity = entity(trace);
                    WitnessEffect a = new WitnessEffect(1, "A" + index, trace);
                    WitnessEffect b = new WitnessEffect(2, "B" + index, trace);
                    entity.getEffects().put(1, a);
                    entity.getEffects().put(2, b);
                    a.once.put(Stage.APPLY, () -> {
                        try { barrier.await(10, TimeUnit.SECONDS); }
                        catch (Exception error) { throw new AssertionError(error); }
                    });
                    entity.entityBaseTick(1);
                    assertEquals(concat(steps("A" + index, 100, 99), steps("B" + index, 100, 99)), trace);
                    Object lease = assertLeaseEmpty();
                    if (lease != null) leases.add(lease);
                    return null;
                }));
            }
            for (Future<Void> future : futures) future.get(15, TimeUnit.SECONDS);
            assertTrue(leases.isEmpty() || leases.size() == 4);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
