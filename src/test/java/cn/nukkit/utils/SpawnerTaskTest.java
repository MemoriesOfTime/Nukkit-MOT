package cn.nukkit.utils;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.mob.EntityEnderman;
import cn.nukkit.entity.mob.EntityWitch;
import cn.nukkit.entity.passive.EntityCod;
import cn.nukkit.entity.passive.EntityCow;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SpawnerTaskTest {

    @Test
    void saturatedCowCapStopsBeforeTheUnrelatedWorldTail() {
        SaturationFixture f = new SaturationFixture(Level.DIMENSION_OVERWORLD);
        Entity tail = mob(EntityWitch.NETWORK_ID, 0, 64, 0, true);
        Entity[] entities = new Entity[1002];
        Arrays.fill(entities, tail);
        entities[0] = mob(EntityCow.NETWORK_ID, 1, 64, 0, true);
        entities[1] = mob(EntityCow.NETWORK_ID, -1, 64, 0, true);
        when(f.level.getEntities()).thenReturn(entities);
        assertFalse(SpawnerTask.entitySpawnAllowed(f.level, EntityCow.NETWORK_ID, f.player));
        verify(tail, never()).isAlive();
    }

    @Test
    void equalityRejectsAllExistingSpeciesCaps() {
        int[][] cases = {
                {Level.DIMENSION_OVERWORLD, EntityWitch.NETWORK_ID, 1},
                {Level.DIMENSION_OVERWORLD, EntityCow.NETWORK_ID, 2},
                {Level.DIMENSION_OVERWORLD, EntityCod.NETWORK_ID, 4},
                {Level.DIMENSION_THE_END, EntityEnderman.NETWORK_ID, 10}
        };
        for (int[] test : cases) {
            SaturationFixture f = new SaturationFixture(test[0]);
            Entity[] entities = new Entity[test[2]];
            for (int i = 0; i < entities.length; i++) {
                entities[i] = mob(test[1], i, 64, 0, true);
            }
            when(f.level.getEntities()).thenReturn(entities);
            assertFalse(SpawnerTask.entitySpawnAllowed(f.level, test[1], f.player));
            when(f.level.getEntities()).thenReturn(Arrays.copyOf(entities, entities.length - 1));
            assertTrue(SpawnerTask.entitySpawnAllowed(f.level, test[1], f.player));
        }
    }

    @Test
    void radiusRemainsStrictHorizontal128AndFiltersRemainUnchanged() {
        SaturationFixture f = new SaturationFixture(Level.DIMENSION_OVERWORLD);
        f.player.x = -128;
        f.player.z = -128;
        Entity[] outsideAndFiltered = {
                mob(EntityCow.NETWORK_ID, -127, 10000, -128, true),
                mob(EntityCow.NETWORK_ID, 0, 64, -128, true),
                mob(EntityCow.NETWORK_ID, -128, 64, -256, true),
                mob(EntityCow.NETWORK_ID, -128, 64, -128, false),
                mob(EntityWitch.NETWORK_ID, -128, 64, -128, true)
        };
        when(f.level.getEntities()).thenReturn(outsideAndFiltered);
        assertTrue(SpawnerTask.entitySpawnAllowed(f.level, EntityCow.NETWORK_ID, f.player));
        Entity[] inside = {
                mob(EntityCow.NETWORK_ID, -127, 10000, -128, true),
                mob(EntityCow.NETWORK_ID, -0.01, -10000, -128, true)
        };
        when(f.level.getEntities()).thenReturn(inside);
        assertFalse(SpawnerTask.entitySpawnAllowed(f.level, EntityCow.NETWORK_ID, f.player));
    }

    @Test
    void disabledDimensionDoesNotReadWorldEntities() {
        SaturationFixture f = new SaturationFixture(Level.DIMENSION_NETHER);
        assertFalse(SpawnerTask.entitySpawnAllowed(f.level, EntityCow.NETWORK_ID, f.player));
        verify(f.level, never()).getEntities();
    }

    private static Entity mob(int id, double x, double y, double z, boolean alive) {
        Entity entity = mock(Entity.class);
        entity.x = x;
        entity.y = y;
        entity.z = z;
        when(entity.isAlive()).thenReturn(alive);
        when(entity.getNetworkId()).thenReturn(id);
        return entity;
    }

    private static final class SaturationFixture {
        final Level level = mock(Level.class);
        final Player player = mock(Player.class);
        SaturationFixture(int dimension) {
            when(level.getDimension()).thenReturn(dimension);
        }
    }

    private static final Class<?>[] IDLE_KEYS = {
            String.class, Integer.class, Long.class, Short.class, Byte.class, Double.class,
            Float.class, Character.class, Boolean.class, Object.class, Number.class,
            Thread.class, Runnable.class, List.class, Collection.class, Iterator.class,
            StringBuilder.class, Math.class, System.class, Class.class, Enum.class,
            Record.class, Iterable.class};

    private static final Class<?>[] SPREAD_KEYS = {
            String.class, Integer.class, Long.class, Short.class, Byte.class, Double.class, Float.class,
            Character.class, Boolean.class, Object.class, Number.class, Thread.class, Runnable.class,
            List.class, Map.class, Set.class, StringBuilder.class, Math.class, System.class, Class.class,
            Enum.class, Record.class, Iterable.class};

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
            map.put(IDLE_KEYS[i], new Spawner(field + i, clock, calls));
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
            for (int count = 0; count <= IDLE_KEYS.length; count++) {
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
            fill(task, "mobSpawners", IDLE_KEYS.length, clock, calls);
            fill(task, "animalSpawners", IDLE_KEYS.length, clock, calls);
            for (int tick : new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -1, 0, 1,
                    Integer.MAX_VALUE - 1, Integer.MAX_VALUE}) {
                calls.clear();
                clock[0] = tick;
                task.tick(tick, true, true, true);
                List<String> expected = new ArrayList<>();
                String kind = (Math.floorDiv(tick, period) & 1) == 0 ? "mobSpawners" : "animalSpawners";
                for (int index = 0; index < IDLE_KEYS.length; index++) {
                    int slot = 1 + (int) ((long) index * (period - 1) / IDLE_KEYS.length);
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

    /**
     * The spawner used to start every spawner of a turn in one tick, for every player in every level, every
     * 100 ticks. Each spawner of the turn now starts at its own tick of the period.
     */
    private static final class Counting implements EntitySpawner {
        final IntArrayList startedAt = new IntArrayList();
        final int[] clock;

        Counting(int[] clock) {
            this.clock = clock;
        }

        @Override
        public void spawn() {
            this.startedAt.add(this.clock[0]);
        }

        @Override
        public void spawn(Player player, Position pos, Level level) {
        }

        @Override
        public int getEntityNetworkId() {
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Counting> fill(SpawnerTask task, String field, int count, int[] clock) throws Exception {
        Field map = SpawnerTask.class.getDeclaredField(field);
        map.setAccessible(true);
        Map<Class<?>, EntitySpawner> spawners = (Map<Class<?>, EntitySpawner>) map.get(task);
        List<Counting> added = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Counting spawner = new Counting(clock);
            spawners.put(SPREAD_KEYS[i], spawner);
            added.add(spawner);
        }
        return added;
    }

    @Test
    void eachSpawnerStartsOncePerTurnAtItsOwnTickAndMonstersAndAnimalsTakeTurns() throws Exception {
        SpawnerTask task = new SpawnerTask(100, false);
        int[] clock = new int[1];
        List<Counting> monsters = fill(task, "mobSpawners", 18, clock);
        List<Counting> animals = fill(task, "animalSpawners", 23, clock);

        int[] startsPerTick = new int[400];
        for (int tick = 0; tick < 400; tick++) {
            clock[0] = tick;
            int before = total(monsters) + total(animals);
            task.tick(tick, true, true, true);
            startsPerTick[tick] = total(monsters) + total(animals) - before;
        }

        for (Counting monster : monsters) {
            assertEquals(2, monster.startedAt.size(), "once every 200 ticks");
            assertEquals(200, monster.startedAt.getInt(1) - monster.startedAt.getInt(0));
            assertTrue(monster.startedAt.getInt(0) < 100, "monsters take the first turn");
        }
        for (Counting animal : animals) {
            assertEquals(2, animal.startedAt.size(), "once every 200 ticks");
            assertTrue(animal.startedAt.getInt(0) >= 100 && animal.startedAt.getInt(0) < 200, "animals take the next turn");
        }
        for (int tick = 0; tick < 400; tick++) {
            assertTrue(startsPerTick[tick] <= 1, "one spawner per tick, " + startsPerTick[tick] + " at " + tick);
            if (tick % 100 == 0) {
                assertEquals(0, startsPerTick[tick], "nothing on the tick shared by every 100-tick period");
            }
        }
    }

    @Test
    void nothingStartsWithoutPlayersOrWhenTheKindIsDisabled() throws Exception {
        SpawnerTask task = new SpawnerTask(100, false);
        int[] clock = new int[1];
        List<Counting> monsters = fill(task, "mobSpawners", 3, clock);
        List<Counting> animals = fill(task, "animalSpawners", 3, clock);

        for (int tick = 0; tick < 400; tick++) {
            task.tick(tick, false, true, true);
            task.tick(tick, true, false, false);
        }

        assertEquals(0, total(monsters) + total(animals));
    }

    @Test
    void slotsCoverThePeriodWithoutItsFirstTick() {
        for (int count = 1; count <= 99; count++) {
            Set<Integer> slots = new HashSet<>();
            for (int index = 0; index < count; index++) {
                int slot = SpawnerTask.slot(index, count, 100);
                assertTrue(slot >= 1 && slot <= 99, "slot " + slot);
                slots.add(slot);
            }
            assertEquals(count, slots.size(), "distinct slots for " + count + " spawners");
        }
        assertEquals(0, SpawnerTask.slot(0, 5, 1), "a one-tick period starts everything every tick");
        assertNotEquals(0, SpawnerTask.slot(0, 5, 2));
    }

    private static int total(List<Counting> spawners) {
        int sum = 0;
        for (Counting spawner : spawners) {
            sum += spawner.startedAt.size();
        }
        return sum;
    }
}
