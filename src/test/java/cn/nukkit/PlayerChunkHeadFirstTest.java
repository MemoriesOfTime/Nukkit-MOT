package cn.nukkit;

import cn.nukkit.level.Level;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.plugin.PluginManager;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * With async-chunk-load-completion every tick starts at the head of the load queue, so the
 * nearest chunks are read and sent first on join/teleport; reads already in flight do not
 * spend the admission budget, and the tail cursor only uses the remaining scan budget.
 */
class PlayerChunkHeadFirstTest {
    private static void set(Class<?> type, Object target, String name, Object value) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Player player(Level level, Server server) throws Exception {
        Player player = mock(Player.class);
        set(cn.nukkit.entity.Entity.class, player, "server", server);
        player.level = level;
        player.connected = true;
        set(Player.class, player, "hasSpawnChunks", true);
        set(Player.class, player, "usedChunks", new Long2ObjectOpenHashMap<>());
        set(Player.class, player, "loadQueue", new LongLinkedOpenHashSet());
        doCallRealMethod().when(player).sendNextChunk();
        return player;
    }

    private static Server server() {
        Server server = mock(Server.class);
        server.chunksPerTick = 10;
        server.asyncChunkLoadCompletion = true;
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        return server;
    }

    @Test
    void readsAreAdmittedNearFirstWhenEarlierReadsAreStillInFlight() throws Exception {
        Level level = mock(Level.class);
        when(level.isAsyncChunkLoadEnabled()).thenReturn(true);
        Set<Integer> inFlight = new HashSet<>();
        List<Integer> order = new ArrayList<>();
        when(level.isChunkLoadPending(anyInt(), anyInt())).thenAnswer(call -> inFlight.contains((Integer) call.getArgument(0)));
        when(level.requestChunkLoadAsync(anyInt(), anyInt())).thenAnswer(call -> {
            int x = call.getArgument(0);
            if (inFlight.add(x)) order.add(x);
            return true;
        });
        Player player = player(level, server());
        for (int x = 0; x < 800; x++) player.loadQueue.add(Level.chunkHash(x, 0));   // sorted near -> far
        for (int t = 0; t < 4; t++) player.sendNextChunk();
        assertEquals(40, order.size());
        for (int i = 0; i < order.size(); i++) {
            assertEquals(i, order.get(i), "reads must follow queue order: " + order);
        }
    }

    @Test
    void nearestChunkIsSentOnTheTickItBecomesReady() throws Exception {
        Level level = mock(Level.class);
        when(level.isAsyncChunkLoadEnabled()).thenReturn(true);
        when(level.isChunkLoadPending(anyInt(), anyInt())).thenReturn(true);
        AtomicInteger tick = new AtomicInteger();
        AtomicInteger sentAt = new AtomicInteger(-1);
        doAnswer(call -> { if (sentAt.get() < 0) sentAt.set(tick.get()); return null; })
                .when(level).requestChunk(eq(0), eq(0), any());
        Player player = player(level, server());
        for (int x = 0; x < 800; x++) player.loadQueue.add(Level.chunkHash(x, 0));
        for (int t = 1; t <= 5; t++) {                              // cursor moves deep into the tail
            tick.set(t);
            player.sendNextChunk();
        }
        BaseFullChunk ready = mock(BaseFullChunk.class);
        when(ready.isPopulated()).thenReturn(true);
        when(level.getChunkIfLoaded(0, 0)).thenReturn(ready);
        when(level.populateChunk(0, 0)).thenReturn(true);
        tick.set(6);
        player.sendNextChunk();
        assertEquals(6, sentAt.get(), "head chunk must not wait for the tail cursor to wrap");
    }

    @Test
    void tailScanStillAdvancesBehindAPendingHead() throws Exception {
        Level level = mock(Level.class);
        when(level.isAsyncChunkLoadEnabled()).thenReturn(true);
        when(level.isChunkLoadPending(anyInt(), anyInt())).thenReturn(true);
        Player player = player(level, server());
        for (int x = 0; x < 400; x++) player.loadQueue.add(Level.chunkHash(x, 0));
        BaseFullChunk ready = mock(BaseFullChunk.class);
        when(ready.isPopulated()).thenReturn(true);
        when(level.getChunkIfLoaded(399, 0)).thenReturn(ready);
        when(level.populateChunk(399, 0)).thenReturn(true);
        int ticks = 0;
        while (player.loadQueue.contains(Level.chunkHash(399, 0)) && ticks < 50) {
            clearInvocations(level);
            player.sendNextChunk();
            long visits = mockingDetails(level).getInvocations().stream()
                    .filter(i -> i.getMethod().getName().equals("getChunkIfLoaded")).count();
            assertTrue(visits <= 100, "bounded scan: " + visits);
            ticks++;
        }
        // head 20 + tail 40 per tick: 380 tail entries take ~10 ticks.
        assertTrue(ticks <= 11, "tail reached after " + ticks + " ticks");
        verify(level).requestChunk(399, 0, player);
    }
}
