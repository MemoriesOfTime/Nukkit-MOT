package cn.nukkit;

import cn.nukkit.level.Level;
import cn.nukkit.plugin.PluginManager;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Corrected review v2 probe: pending queues have bounded visits and make fair progress. */
class ReviewV2SendScanBoundProbeTest {
    private static void set(Class<?> type, Object target, String name, Object value) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private int visitsPerTick(boolean completion) throws Exception {
        Level level = mock(Level.class);
        Server server = mock(Server.class);
        server.chunksPerTick = 10;
        server.asyncChunkLoadCompletion = completion;
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        when(level.isAsyncChunkLoadEnabled()).thenReturn(true);
        when(level.requestChunkLoadAsync(anyInt(), anyInt())).thenReturn(true);   // reads accepted, still in flight
        Player player = mock(Player.class);
        set(cn.nukkit.entity.Entity.class, player, "server", server);
        player.level = level;
        player.connected = true;
        set(Player.class, player, "hasSpawnChunks", true);
        set(Player.class, player, "usedChunks", new Long2ObjectOpenHashMap<>());
        set(Player.class, player, "loadQueue", new LongLinkedOpenHashSet());
        doCallRealMethod().when(player).sendNextChunk();
        for (int x = 0; x < 28; x++) for (int z = 0; z < 28; z++) player.loadQueue.add(Level.chunkHash(x, z)); // ~radius 16
        player.sendNextChunk();
        return mockingDetails(level).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("registerChunkLoader")).mapToInt(i -> 1).sum();
    }

    @Test
    void pendingQueueVisitsAreBoundedEveryTick() throws Exception {
        int legacy = visitsPerTick(false);
        int completion = visitsPerTick(true);
        System.out.println("REVIEW visits per tick: flag off=" + legacy + ", flag on=" + completion);
        assertEquals(10, legacy);
        assertTrue(completion <= 100, "pending scan must be bounded independently of queue size");
    }
    @Test
    void boundedCursorReachesHealthyTailWithoutSpendingSendQuotaOnPendingReads() throws Exception {
        Level level = mock(Level.class);
        Server server = mock(Server.class);
        server.chunksPerTick = 10;
        server.asyncChunkLoadCompletion = true;
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        when(level.isAsyncChunkLoadEnabled()).thenReturn(true);
        when(level.requestChunkLoadAsync(anyInt(), anyInt())).thenReturn(true);
        Player player = mock(Player.class);
        set(cn.nukkit.entity.Entity.class, player, "server", server);
        player.level = level;
        player.connected = true;
        set(Player.class, player, "hasSpawnChunks", true);
        set(Player.class, player, "usedChunks", new Long2ObjectOpenHashMap<>());
        set(Player.class, player, "loadQueue", new LongLinkedOpenHashSet());
        doCallRealMethod().when(player).sendNextChunk();
        for (int x = 0; x < 784; x++) player.loadQueue.add(Level.chunkHash(x, 0));
        for (int x = 784; x < 799; x++) {
            var ready = mock(cn.nukkit.level.format.generic.BaseFullChunk.class);
            when(ready.isPopulated()).thenReturn(true);
            when(level.getChunkIfLoaded(x, 0)).thenReturn(ready);
            when(level.populateChunk(x, 0)).thenReturn(true);
            player.loadQueue.add(Level.chunkHash(x, 0));
        }
        int sent = 0;
        for (int tick = 0; tick < 22; tick++) {
            clearInvocations(level);
            player.sendNextChunk();
            long visits = mockingDetails(level).getInvocations().stream()
                    .filter(i -> i.getMethod().getName().equals("getChunkIfLoaded")).count();
            long tickSends = mockingDetails(level).getInvocations().stream()
                    .filter(i -> i.getMethod().getName().equals("requestChunk")).count();
            assertTrue(visits <= 100, "visit budget includes skipped reads");
            assertTrue(tickSends <= 10, "separate send budget");
            sent += tickSends;
        }
        assertEquals(15, sent, "pending prefix cannot starve ready tail");
        assertEquals(784, player.loadQueue.size());
        // Rebuilt queues may remove the cursor; the next pass must safely restart.
        player.loadQueue.clear();
        player.loadQueue.add(Level.chunkHash(784, 0));
        clearInvocations(level);
        player.sendNextChunk();
        verify(level).requestChunk(784, 0, player);
    }

    @Test
    void quarantineSkipsAlsoCountAgainstVisitBudget() throws Exception {
        Level level = mock(Level.class);
        Server server = mock(Server.class);
        server.chunksPerTick = 10;
        server.asyncChunkLoadCompletion = true;
        var placeholder = mock(cn.nukkit.level.format.generic.BaseFullChunk.class);
        when(placeholder.isReadFailurePlaceholder()).thenReturn(true);
        when(level.getChunkIfLoaded(anyInt(), anyInt())).thenReturn(placeholder);
        Player player = mock(Player.class);
        set(cn.nukkit.entity.Entity.class, player, "server", server);
        player.level = level;
        player.connected = true;
        set(Player.class, player, "hasSpawnChunks", true);
        set(Player.class, player, "usedChunks", new Long2ObjectOpenHashMap<>());
        set(Player.class, player, "loadQueue", new LongLinkedOpenHashSet());
        doCallRealMethod().when(player).sendNextChunk();
        for (int x = 0; x < 784; x++) player.loadQueue.add(Level.chunkHash(x, 0));
        player.sendNextChunk();
        verify(level, times(60)).getChunkIfLoaded(anyInt(), anyInt());
        verify(level, never()).requestChunk(anyInt(), anyInt(), any());
    }

}
