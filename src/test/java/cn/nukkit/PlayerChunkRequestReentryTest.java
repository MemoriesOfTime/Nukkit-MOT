package cn.nukkit;

import cn.nukkit.event.Event;
import cn.nukkit.event.player.PlayerChunkRequestEvent;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.plugin.PluginManager;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlayerChunkRequestReentryTest {
    @Test
    void sameWorldReorderInsideChunkRequestEventCannotContinueOldQueueIterator() throws Exception {
        Player player = mock(Player.class);
        Level level = mock(Level.class);
        Server server = mock(Server.class);
        PluginManager plugins = mock(PluginManager.class);
        field(player, "usedChunks", new Long2ObjectOpenHashMap<Boolean>());
        field(player, "loadQueue", new LongLinkedOpenHashSet());
        field(player, "server", server);
        field(player, "hasSpawnChunks", true);
        player.level = level;
        player.connected = true;
        player.spawned = true;
        player.chunkRadius = 3;
        server.chunksPerTick = 3;
        server.spawnThreshold = Integer.MAX_VALUE;
        when(server.getPluginManager()).thenReturn(plugins);
        when(level.getChunkIfLoaded(anyInt(), anyInt())).thenReturn(mock(BaseFullChunk.class));
        when(level.populateChunk(anyInt(), anyInt())).thenReturn(true);
        when(level.getChunkEntities(anyInt(), anyInt(), eq(false))).thenReturn(Map.of());
        for (int x = 100; x < 103; x++) player.loadQueue.add(Level.chunkHash(x, 0));
        doCallRealMethod().when(player).sendNextChunk();
        doCallRealMethod().when(player).orderChunks();
        doCallRealMethod().when(player).unloadChunk(anyInt(), anyInt());
        doCallRealMethod().when(player).unloadChunk(anyInt(), anyInt(), nullable(Level.class));
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger reorderedSize = new AtomicInteger();
        doAnswer(call -> {
            Event event = call.getArgument(0);
            if (event instanceof PlayerChunkRequestEvent && calls.getAndIncrement() == 0) {
                player.x = 200 * 16;
                player.orderChunks();
                reorderedSize.set(player.loadQueue.size());
            }
            return null;
        }).when(plugins).callEvent(any(Event.class));

        player.sendNextChunk();

        assertAll(
                () -> verify(level, never()).requestChunk(eq(100), eq(0), same(player)),
                () -> assertEquals(reorderedSize.get(), player.loadQueue.size(), "replacement queue must remain intact"),
                () -> assertEquals(1, calls.get(), "old iterator must stop after a nested reorder"));
    }

    @Test
    void worldSwitchInsideChunkRequestEventCannotRequestOldCoordinatesInNewWorld() throws Exception {
        Player player = mock(Player.class);
        Level original = mock(Level.class), destination = mock(Level.class);
        Server server = mock(Server.class);
        PluginManager plugins = mock(PluginManager.class);
        BaseFullChunk loaded = mock(BaseFullChunk.class);
        field(player, "usedChunks", new Long2ObjectOpenHashMap<Boolean>());
        field(player, "loadQueue", new LongLinkedOpenHashSet());
        field(player, "server", server);
        field(player, "hasSpawnChunks", true);
        player.level = original;
        player.connected = true;
        player.spawned = true;
        server.chunksPerTick = 3;
        server.spawnThreshold = Integer.MAX_VALUE;
        when(server.getPluginManager()).thenReturn(plugins);
        for (Level level : new Level[] { original, destination }) {
            when(level.getChunkIfLoaded(anyInt(), anyInt())).thenReturn(loaded);
            when(level.populateChunk(anyInt(), anyInt())).thenReturn(true);
            when(level.getChunkEntities(anyInt(), anyInt(), eq(false))).thenReturn(Map.of());
        }
        for (int x = 100; x < 103; x++) player.loadQueue.add(Level.chunkHash(x, 0));
        doCallRealMethod().when(player).sendNextChunk();
        doCallRealMethod().when(player).preSwitchLevel();
        AtomicInteger calls = new AtomicInteger();
        long destinationIndex = Level.chunkHash(200, 0);
        doAnswer(call -> {
            Event event = call.getArgument(0);
            if (event instanceof PlayerChunkRequestEvent && calls.getAndIncrement() == 0) {
                player.preSwitchLevel();
                player.level = destination;
                player.loadQueue.add(destinationIndex);
            }
            return null;
        }).when(plugins).callEvent(any(Event.class));

        player.sendNextChunk();

        assertAll(
                () -> verify(destination, never()).requestChunk(eq(100), eq(0), same(player)),
                () -> assertTrue(player.loadQueue.contains(destinationIndex), "new world owns a fresh queue"),
                () -> assertEquals(1, player.loadQueue.size(), "old iterator must not consume or corrupt replacement queue"),
                () -> assertEquals(1, calls.get(), "old chunk pass must stop after world/epoch changes"));
    }

    private static void field(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }
}
