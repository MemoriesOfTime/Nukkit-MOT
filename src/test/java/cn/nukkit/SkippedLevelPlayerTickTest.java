package cn.nukkit;

import cn.nukkit.level.Level;
import cn.nukkit.level.format.LevelProvider;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import static org.mockito.Mockito.*;

class SkippedLevelPlayerTickTest {
    @Test void throttledWorldStillTicksSpawnedPlayer() throws Exception {
        Fixture f = new Fixture(false); f.tick();
        verify(f.player).onUpdate(100); verify(f.level, never()).doTick(anyInt());
    }
    @Test void alwaysTickDoesNotTickPlayerTwice() throws Exception {
        Fixture f = new Fixture(true); f.tick();
        verify(f.player, times(1)).onUpdate(100);
    }
    @Test void conversionNeverRunsFallbackPlayerTicks() throws Exception {
        Fixture f = new Fixture(false); f.level.isBeingConverted = true; f.tick();
        verify(f.player, never()).onUpdate(anyInt());
    }
    @Test void unspawnedPlayerDoesNotGainGameplayTicks() throws Exception {
        Fixture f = new Fixture(false); f.player.spawned = false; f.tick();
        verify(f.player, never()).onUpdate(anyInt());
    }
    @Test void playerThatLeftLevelDoesNotRunFromOldSnapshot() throws Exception {
        Fixture f = new Fixture(false); f.player.level = mock(Level.class); f.tick();
        verify(f.player, never()).onUpdate(anyInt());
    }
    @Test void closedPlayerDoesNotGainGameplayTicks() throws Exception {
        Fixture f = new Fixture(false); f.player.closed = true; f.tick();
        verify(f.player, never()).onUpdate(anyInt());
    }
    @Test void teleportDuringTickCannotInvalidateSnapshotOrTickPlayerInOldWorld() throws Exception {
        Fixture f = new Fixture(false);
        Player other = mock(Player.class); other.spawned = true; other.level = f.level;
        Map<Long,Player> players = new LinkedHashMap<>(); players.put(1L,f.player); players.put(2L,other);
        when(f.level.getPlayers()).thenReturn(players);
        when(f.player.onUpdate(100)).thenAnswer(a -> { players.remove(2L); other.level = mock(Level.class); return true; });
        f.tick();
        verify(f.player).onUpdate(100); verify(other, never()).onUpdate(anyInt());
    }
    @ParameterizedTest @ValueSource(ints = {1,4})
    void dueWorldTickStillOwnsNormalPlayerScheduling(int tickRate) throws Exception {
        Fixture f = new Fixture(false); when(f.level.getTickRate()).thenReturn(tickRate);
        f.level.tickRateCounter = 1;
        Field lock = Level.class.getDeclaredField("providerLock"); lock.setAccessible(true);
        lock.set(f.level, new ReentrantReadWriteLock());
        when(f.level.getProvider()).thenReturn(mock(LevelProvider.class));
        f.tick();
        verify(f.level).doTick(100); verify(f.player, never()).onUpdate(anyInt());
    }
    private static final class Fixture {
        final Server server = mock(Server.class, CALLS_REAL_METHODS);
        final Level level = mock(Level.class);
        final Player player = mock(Player.class);
        Fixture(boolean alwaysTick) throws Exception {
            set(server, "alwaysTickPlayers", alwaysTick); set(server, "baseTickRate", 1);
            set(server, "levelArray", new Level[]{level});
            set(server, "players", Map.of(new InetSocketAddress("127.0.0.1",19132),player));
            set(server, "playerList", new HashMap<UUID,Player>());
            player.spawned = true; player.level = level;
            when(level.getTickRate()).thenReturn(4); level.tickRateCounter = 3;
            when(level.getPlayers()).thenReturn(Map.of(1L,player));
        }
        void tick() throws Exception { Method m=Server.class.getDeclaredMethod("checkTickUpdates",int.class);m.setAccessible(true);m.invoke(server,100); }
    }
    private static void set(Server server,String name,Object value) throws Exception {
        Field f=Server.class.getDeclaredField(name);f.setAccessible(true);f.set(server,value);
    }
}
