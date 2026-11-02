package cn.nukkit.utils;

import cn.nukkit.Player;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The spawner used to start every spawner of a turn in one tick, for every player in every level, every
 * 100 ticks. Each spawner of the turn now starts at its own tick of the period.
 */
class SpawnerTaskSpreadTest {

    private static final Class<?>[] KEYS = {
            String.class, Integer.class, Long.class, Short.class, Byte.class, Double.class, Float.class,
            Character.class, Boolean.class, Object.class, Number.class, Thread.class, Runnable.class,
            List.class, Map.class, Set.class, StringBuilder.class, Math.class, System.class, Class.class,
            Enum.class, Record.class, Iterable.class};

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
            spawners.put(KEYS[i], spawner);
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
