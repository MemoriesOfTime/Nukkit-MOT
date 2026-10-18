package cn.nukkit.utils;

import cn.nukkit.Player;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpawnerTaskIdlePhaseTest {
    private static final Class<?>[] KEYS = {
            String.class, Integer.class, Long.class, Short.class, Byte.class, Double.class,
            Float.class, Character.class, Boolean.class, Object.class, Number.class,
            Thread.class, Runnable.class, List.class, Collection.class, Iterator.class,
            StringBuilder.class, Math.class, System.class, Class.class, Enum.class,
            Record.class, Iterable.class};

    private static final class CountingMap extends LinkedHashMap<Class<?>, EntitySpawner> {
        int scans;
        int visits;

        @Override
        public Collection<EntitySpawner> values() {
            scans++;
            Collection<EntitySpawner> values = super.values();
            return new AbstractCollection<>() {
                @Override
                public int size() { return values.size(); }

                @Override
                public Iterator<EntitySpawner> iterator() {
                    Iterator<EntitySpawner> entries = values.iterator();
                    return new Iterator<>() {
                        @Override
                        public boolean hasNext() { return entries.hasNext(); }

                        @Override
                        public EntitySpawner next() { visits++; return entries.next(); }
                    };
                }
            };
        }
    }

    private record Spawner(String name, int[] clock, List<String> calls) implements EntitySpawner {
        @Override
        public void spawn() { calls.add(clock[0] + ":" + name); }

        @Override
        public void spawn(Player player, Position pos, Level level) { }

        @Override
        public int getEntityNetworkId() { return 0; }
    }

    private static CountingMap fill(SpawnerTask task, String field, int count,
                                    int[] clock, List<String> calls) throws Exception {
        CountingMap map = new CountingMap();
        for (int i = 0; i < count; i++) {
            map.put(KEYS[i], new Spawner(field + i, clock, calls));
        }
        Field target = SpawnerTask.class.getDeclaredField(field);
        target.setAccessible(true);
        target.set(task, map);
        return map;
    }

    @Test
    void defaultCycleSkipsOnlyEmptyPhasesAndKeepsAllFortyOneStarts() throws Exception {
        SpawnerTask task = new SpawnerTask(100, false);
        int[] clock = new int[1];
        List<String> calls = new ArrayList<>();
        CountingMap monsters = fill(task, "mobSpawners", 18, clock, calls);
        CountingMap animals = fill(task, "animalSpawners", 23, clock, calls);
        for (int tick = 0; tick < 200; tick++) {
            clock[0] = tick;
            task.tick(tick, true, true, true);
        }
        System.out.println("p07-cycle: scans=" + (monsters.scans + animals.scans)
                + " visits=" + (monsters.visits + animals.visits) + " starts=" + calls.size());
        assertEquals(41, calls.size());
        assertEquals(853, monsters.visits + animals.visits);
        assertEquals(41, monsters.scans + animals.scans);
    }

    @Test
    void emptyRegistryNeverCreatesAnIterator() throws Exception {
        SpawnerTask task = new SpawnerTask(100, false);
        CountingMap monsters = fill(task, "mobSpawners", 0, new int[1], new ArrayList<>());
        CountingMap animals = fill(task, "animalSpawners", 0, new int[1], new ArrayList<>());
        for (int tick = 0; tick < 200; tick++) task.tick(tick, true, true, true);
        assertEquals(0, monsters.scans + animals.scans);
    }

    @Test
    void spawnTraceMatchesOriginalSlotsIncludingDensePeriodsAndNegativeTicks() throws Exception {
        for (int period : new int[]{1, 2, 3, 5, 17, 100, 257}) {
            for (int count = 0; count <= KEYS.length; count++) {
                SpawnerTask task = new SpawnerTask(period, false);
                int[] clock = new int[1];
                List<String> calls = new ArrayList<>();
                fill(task, "mobSpawners", count, clock, calls);
                fill(task, "animalSpawners", count, clock, calls);
                for (int tick = -2 * period; tick <= 2 * period; tick++) {
                    calls.clear();
                    clock[0] = tick;
                    task.tick(tick, true, true, true);
                    List<String> expected = new ArrayList<>();
                    String kind = (Math.floorDiv(tick, period) & 1) == 0 ? "mobSpawners" : "animalSpawners";
                    for (int index = 0; index < count; index++) {
                        int slot = period == 1 ? 0 : 1 + (int) ((long) index * (period - 1) / count);
                        if (slot == Math.floorMod(tick, period)) expected.add(tick + ":" + kind + index);
                    }
                    assertEquals(expected, calls, "period=" + period + " count=" + count + " tick=" + tick);
                }
            }
        }
    }

    @Test
    void largePeriodsAndIntegerTickBoundariesKeepTheOriginalTrace() throws Exception {
        for (int period : new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE - 1, 1_000_000_007}) {
            SpawnerTask task = new SpawnerTask(period, false);
            int[] clock = new int[1];
            List<String> calls = new ArrayList<>();
            fill(task, "mobSpawners", KEYS.length, clock, calls);
            fill(task, "animalSpawners", KEYS.length, clock, calls);
            for (int tick : new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -1, 0, 1,
                    Integer.MAX_VALUE - 1, Integer.MAX_VALUE}) {
                calls.clear();
                clock[0] = tick;
                task.tick(tick, true, true, true);
                List<String> expected = new ArrayList<>();
                String kind = (Math.floorDiv(tick, period) & 1) == 0 ? "mobSpawners" : "animalSpawners";
                for (int index = 0; index < KEYS.length; index++) {
                    int slot = 1 + (int) ((long) index * (period - 1) / KEYS.length);
                    if (slot == Math.floorMod(tick, period)) expected.add(tick + ":" + kind + index);
                }
                assertEquals(expected, calls, "period=" + period + " tick=" + tick);
            }
        }
    }

    @Test
    void registryChangesAreVisibleOnTheNextTickWithoutStalePhaseState() throws Exception {
        SpawnerTask task = new SpawnerTask(100, false);
        int[] clock = new int[1];
        List<String> calls = new ArrayList<>();
        CountingMap monsters = fill(task, "mobSpawners", 0, clock, calls);
        fill(task, "animalSpawners", 0, clock, calls);
        task.tick(0, true, true, true);
        monsters.put(String.class, new Spawner("first", clock, calls));
        clock[0] = 1;
        task.tick(1, true, true, true);
        monsters.put(Integer.class, new Spawner("second", clock, calls));
        clock[0] = 50;
        task.tick(50, true, true, true);
        monsters.remove(String.class);
        clock[0] = 51;
        task.tick(51, true, true, true);
        clock[0] = 201;
        task.tick(201, true, true, true);
        assertEquals(List.of("1:first", "50:second", "201:second"), calls);
    }
}
