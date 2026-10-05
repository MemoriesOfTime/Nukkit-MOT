package cn.nukkit.level;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.BaseEntity;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.mob.EntitySnowGolem;
import cn.nukkit.entity.mob.EntityZombie;
import cn.nukkit.entity.passive.EntityPig;
import cn.nukkit.entity.passive.EntitySheep;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.potion.Effect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Entity activation: mobs far from every player run once per second and catch up the skipped time.
 * Everything that needs the per-tick cadence (fire, effects, damage, riders, leash, names, owners,
 * plugin subclasses) keeps the mob awake, and the despawn/floor cadences survive the catch-up.
 */
class EntityActivationTest {
    private Server server;
    private double previousRange;
    private Level level;
    private final Map<Long, Player> players = new HashMap<>();

    @BeforeEach
    void setUp() {
        MockServer.init();
        Effect.init();
        server = MockServer.get();
        previousRange = server.entityActivationRangeSquared;
        server.entityActivationRangeSquared = 80 * 80;
        // Every block read of the mocked world is air; everything else keeps Mockito's defaults.
        level = mock(Level.class, withSettings().defaultAnswer(call ->
                cn.nukkit.block.Block.class.isAssignableFrom(call.getMethod().getReturnType())
                        ? cn.nukkit.block.Block.get(cn.nukkit.block.Block.AIR)
                        : call.getMethod().getReturnType().isArray()
                        ? java.lang.reflect.Array.newInstance(call.getMethod().getReturnType().getComponentType(), 0)
                        : RETURNS_DEFAULTS.answer(call)));
        when(level.getServer()).thenReturn(server);
        when(level.getGameRules()).thenReturn(GameRules.getDefault());
        when(level.getChunkPlayers(anyInt(), anyInt())).thenReturn(Collections.emptyMap());
        when(level.getPlayers()).thenReturn(players);
        level.isBeingConverted = true;
        // A fresh mob counts as recently hurt until the server is past the damage grace window.
        when(server.getTick()).thenReturn(100_000);
    }

    @AfterEach
    void tearDown() {
        server.entityActivationRangeSquared = previousRange;
        when(server.getTick()).thenReturn(0);
    }

    private FullChunk chunk(double x, double z) {
        FullChunk chunk = mock(FullChunk.class);
        when(chunk.getX()).thenReturn((int) Math.floor(x) >> 4);
        when(chunk.getZ()).thenReturn((int) Math.floor(z) >> 4);
        LevelProvider provider = mock(LevelProvider.class);
        when(chunk.getProvider()).thenReturn(provider);
        when(provider.getLevel()).thenReturn(level);
        return chunk;
    }

    private <T extends Entity> T spawn(BiFunction<FullChunk, CompoundTag, T> factory, double x, double z) {
        T entity = factory.apply(chunk(x, z), Entity.getDefaultNBT(new Vector3(x, 64, z)));
        entity.level = level;
        return entity;
    }

    private void playerAt(double x, double z) {
        Player player = mock(Player.class);
        player.x = x;
        player.z = z;
        players.put((long) players.size() + 1, player);
    }

    /** Ticks in [from, from + count) on which the level would run the entity. */
    private static int runs(Entity entity, int from, int count) {
        int runs = 0;
        for (int tick = from; tick < from + count; tick++) {
            if (!entity.isActivationThrottled(tick)) {
                runs++;
            }
        }
        return runs;
    }

    @Test
    void farMobRunsOncePerSecondAfterItsPhaseTick() {
        playerAt(0, 0);
        EntityPig pig = spawn(EntityPig::new, 200, 0);
        runs(pig, 1000, 20); // reach the first phase tick
        assertTrue(pig.isActivationAsleep());
        assertEquals(3, runs(pig, 1020, 60));
        assertEquals(1, runs(pig, 2000, 20));
    }

    @Test
    void mobNearAnyPlayerNeverSleeps() {
        playerAt(1000, 1000);
        playerAt(210, 30);
        EntityPig pig = spawn(EntityPig::new, 200, 0);
        assertEquals(100, runs(pig, 1000, 100));
        assertFalse(pig.isActivationAsleep());
    }

    @Test
    void heightIsIgnoredLikeTheWalkingRange() {
        playerAt(200, 0);
        EntityPig pig = spawn(EntityPig::new, 200, 0);
        pig.y = -40;
        assertEquals(100, runs(pig, 1000, 100));
    }

    @Test
    void approachingPlayerWakesTheMobWithinOneInterval() {
        playerAt(0, 0);
        EntityPig pig = spawn(EntityPig::new, 200, 0);
        runs(pig, 1000, 40);
        assertTrue(pig.isActivationAsleep());
        players.values().iterator().next().x = 190;
        int tick = 1040;
        while (pig.isActivationThrottled(tick)) {
            tick++;
        }
        assertTrue(tick - 1040 < 20);
        assertEquals(40, runs(pig, tick + 1, 40));
    }

    @Test
    void disabledRangeKeepsEveryTick() {
        server.entityActivationRangeSquared = 0;
        EntityPig pig = spawn(EntityPig::new, 5000, 0);
        assertEquals(100, runs(pig, 1000, 100));
    }

    @Test
    void anythingNeedingTheTickCadenceKeepsTheMobAwake() throws Exception {
        playerAt(0, 0);

        EntityPig burning = spawn(EntityPig::new, 200, 0);
        burning.setOnFire(5);
        assertEquals(40, runs(burning, 1000, 40));

        EntityPig poisoned = spawn(EntityPig::new, 200, 0);
        poisoned.addEffect(Effect.getEffect(Effect.POISON).setDuration(600));
        assertEquals(40, runs(poisoned, 1000, 40));

        EntityPig named = spawn(EntityPig::new, 200, 0);
        named.setNameTag("Piggy");
        assertEquals(40, runs(named, 1000, 40));

        EntityPig leashed = spawn(EntityPig::new, 200, 0);
        leashed.leash(spawn(EntityPig::new, 201, 0));
        assertEquals(40, runs(leashed, 1000, 40));

        EntityPig hurt = spawn(EntityPig::new, 200, 0);
        Field lastDamage = BaseEntity.class.getDeclaredField("lastDamageTick");
        lastDamage.setAccessible(true);
        lastDamage.setInt(hurt, 950);
        when(server.getTick()).thenReturn(1000);
        try {
            assertEquals(40, runs(hurt, 1000, 40));
        } finally {
            when(server.getTick()).thenReturn(100_000);
        }

        EntityPig carrying = spawn(EntityPig::new, 200, 0);
        carrying.passengers.add(spawn(EntityZombie::new, 200, 0));
        assertEquals(40, runs(carrying, 1000, 40));

        EntityPig dead = spawn(EntityPig::new, 200, 0);
        dead.setHealth(0);
        assertEquals(40, runs(dead, 1000, 40));
    }

    @Test
    void shearedSheepWakeUntilTheirWoolHasRegrown() {
        playerAt(0, 0);
        EntitySheep sheep = spawn(EntitySheep::new, 200, 0);
        runs(sheep, 1000, 40);
        assertTrue(sheep.isActivationAsleep());

        sheep.shear(true);
        assertEquals(60, runs(sheep, 1040, 60), "wool regrowth decrements once per update");
        assertFalse(sheep.isActivationAsleep());

        sheep.shear(false);
        runs(sheep, 1100, 20);
        assertTrue(sheep.isActivationAsleep());
        assertEquals(3, runs(sheep, 1120, 60), "regrown sheep may sleep again");
    }

    @Test
    void phaseTimersAndPluginSubclassesNeverSleep() {
        playerAt(0, 0);
        assertEquals(40, runs(spawn(EntitySnowGolem::new, 200, 0), 1000, 40));
        assertEquals(40, runs(spawn(PluginPig::new, 200, 0), 1000, 40));
    }

    @Test
    void phaseSpreadsMobsOverTheInterval() {
        playerAt(0, 0);
        int[] perTick = new int[20];
        for (int i = 0; i < 200; i++) {
            EntityPig pig = spawn(EntityPig::new, 200 + i, 0);
            runs(pig, 1000, 20);
            for (int tick = 1020; tick < 1040; tick++) {
                if (!pig.isActivationThrottled(tick)) {
                    perTick[tick - 1020]++;
                }
            }
        }
        for (int count : perTick) {
            assertEquals(10, count, "200 sleeping mobs must spread evenly over the 20 phase ticks");
        }
    }

    @Test
    void residueHelperEqualsModuloOnSingleTicksAndFiresOncePerCaughtUpPeriod() {
        for (int period : new int[]{2, 10, 100}) {
            for (int value = -1000; value <= 1000; value++) {
                assertEquals(value % period == 0, PluginPig.hits(value, 1, period, 0), value + " % " + period);
            }
        }
        for (int start = -300; start <= 300; start++) {
            int hits = 0;
            for (int step = 0; step < 5; step++) {
                if (PluginPig.hits(start + step * 20, 20, 100, 0)) {
                    hits++;
                }
            }
            assertEquals(1, hits, "five caught-up ticks of 20 must cross exactly one multiple of 100");
            assertEquals(2, (PluginPig.hits(start, 20, 10, 0) ? 1 : 0) + (PluginPig.hits(start + 20, 20, 10, 0) ? 1 : 0));
        }
    }

    @Test
    void sleepingMobStillDespawnsOnCaughtUpTicks() {
        boolean despawn = server.despawnMobs;
        int despawnTicks = server.mobDespawnTicks;
        server.despawnMobs = true;
        server.mobDespawnTicks = 0;
        try {
            for (int offset = 0; offset < 20; offset++) {
                EntityPig pig = spawn(EntityPig::new, 5000, 0);
                pig.age = 1000 + offset + 1;
                for (int step = 0; step < 5 && !pig.closed; step++) {
                    pig.entityBaseTick(20);
                }
                assertTrue(pig.closed, "a far pig must despawn within five caught-up ticks, age offset " + offset);
            }
        } finally {
            server.despawnMobs = despawn;
            server.mobDespawnTicks = despawnTicks;
        }
    }

    @Test
    void levelSkipsASleepingMobButKeepsItScheduled() {
        playerAt(0, 0);
        EntityPig pig = spy(spawn(EntityPig::new, 200, 0));
        doReturn(true).when(pig).onUpdate(anyInt());
        doCallRealMethod().when(level).updateEntity(any(), anyInt());
        doCallRealMethod().when(level).getActivationSkippedUpdates();
        doCallRealMethod().when(level).getActivationRunUpdates();
        for (int tick = 1000; tick < 1100; tick++) {
            assertTrue(level.updateEntity(pig, tick), "a sleeping mob must stay in the update list");
        }
        // Awake until the first phase tick, then one catch-up per 20 ticks.
        int firstPhase = 1000 + Math.floorMod(-(1000 + (int) (pig.getId() % 20)), 20);
        int expected = firstPhase - 1000 + 1 + (1099 - firstPhase) / 20;
        verify(pig, times(expected)).onUpdate(anyInt());
        assertEquals(100 - expected, level.getActivationSkippedUpdates());
        assertEquals(expected, level.getActivationRunUpdates());

        pig.close();
        assertFalse(level.updateEntity(pig, 2000), "a closed entity still leaves the list");
    }

    /** A plugin mob: its class lives outside the core entity package, so it must keep every tick. */
    static final class PluginPig extends EntityPig {
        PluginPig(FullChunk chunk, CompoundTag nbt) {
            super(chunk, nbt);
        }

        static boolean hits(long start, int span, int period, int residue) {
            return hitsResidue(start, span, period, residue);
        }
    }
}
