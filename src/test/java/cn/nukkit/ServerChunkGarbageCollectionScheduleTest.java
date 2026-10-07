package cn.nukkit;

import cn.nukkit.level.Level;
import cn.nukkit.network.Network;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.scheduler.ServerScheduler;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;

/**
 * Every level used to collect chunk garbage in the same tick (every 100th), which was also the tick of
 * the spawner and of every minute-aligned task. Each level now collects once per 100 ticks at its own tick.
 */
class ServerChunkGarbageCollectionScheduleTest {

    private final Server server = mock(Server.class);
    private final AtomicLong nanos = new AtomicLong(1);

    @BeforeEach
    void prepareActualTickBody() throws Exception {
        set("network", mock(Network.class));
        set("scheduler", mock(ServerScheduler.class));
        set("pluginManager", mock(PluginManager.class));
        set("players", new HashMap<InetSocketAddress, Player>());
        set("tickAverage", new float[20]);
        set("useAverage", new float[20]);
        set("autoSaveTicks", Integer.MAX_VALUE);
        doCallRealMethod().when(this.server).tick(anyLong(), any());
    }

    private void set(String name, Object value) throws Exception {
        Field field = Server.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(this.server, value);
    }

    private static Level level(int id, IntArrayList collectedAt, Server server) throws Exception {
        Level level = mock(Level.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        Field levelId = Level.class.getDeclaredField("levelId");
        levelId.setAccessible(true);
        levelId.set(level, id);
        // Level ticks see a closed level (no provider) and skip it; only the collection schedule is exercised.
        Field providerLock = Level.class.getDeclaredField("providerLock");
        providerLock.setAccessible(true);
        providerLock.set(level, new java.util.concurrent.locks.ReentrantReadWriteLock());
        doAnswer(invocation -> {
            collectedAt.add(server.getTick());
            return null;
        }).when(level).doChunkGarbageCollection();
        return level;
    }

    @Test
    void levelsCollectOncePerHundredTicksEachAtItsOwnTickAndNeverOnTheHundredth() throws Exception {
        doCallRealMethod().when(this.server).getTick();
        IntArrayList[] collected = new IntArrayList[6];
        Level[] levels = new Level[collected.length];
        for (int i = 0; i < levels.length; i++) {
            collected[i] = new IntArrayList();
            levels[i] = level(i + 1, collected[i], this.server);
        }
        set("levelArray", levels);

        for (int tick = 1; tick <= 300; tick++) {
            this.server.tick(tick * 50L, this.nanos::get);
        }

        Set<Integer> phases = new HashSet<>();
        for (IntArrayList ticks : collected) {
            assertEquals(3, ticks.size(), "once per 100 ticks");
            assertEquals(100, ticks.getInt(1) - ticks.getInt(0));
            for (int tick : ticks) {
                assertNotEquals(0, tick % 100, "never in the tick shared by every 100-tick period");
            }
            phases.add(ticks.getInt(0) % 100);
        }
        assertEquals(collected.length, phases.size(), "each level at its own tick");
    }
}
