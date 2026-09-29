package cn.nukkit;

import cn.nukkit.level.Level;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.plugin.PluginManager;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Regression from round-3 review: failed chunks cannot starve healthy sends. */
class ReviewFailedChunkStarvationTest {
    private static void set(Class<?> type, Object target, String name, Object value) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private Player player(Level level, Server server) throws Exception {
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

    @Test
    void tenQuarantinedChunksDoNotBlockHealthyChunks() throws Exception {
        Level level = mock(Level.class);
        Server server = mock(Server.class);
        server.chunksPerTick = 10;
        server.asyncChunkLoadCompletion = true;
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        when(level.isAsyncChunkLoadEnabled()).thenReturn(true);
        Player player = player(level, server);
        for (int i = 0; i < 10; i++) {
            BaseFullChunk bad = mock(BaseFullChunk.class);
            when(bad.isReadFailurePlaceholder()).thenReturn(true);
            when(level.getChunkIfLoaded(i, 0)).thenReturn(bad);
            player.loadQueue.add(Level.chunkHash(i, 0));
        }
        BaseFullChunk healthy = mock(BaseFullChunk.class);
        when(healthy.isPopulated()).thenReturn(true);
        when(level.getChunkIfLoaded(50, 50)).thenReturn(healthy);
        when(level.populateChunk(50, 50)).thenReturn(true);
        player.loadQueue.add(Level.chunkHash(50, 50));
        for (int tick = 0; tick < 200; tick++) player.sendNextChunk();
        verify(level).requestChunk(eq(50), eq(50), any());
        assertFalse(player.loadQueue.contains(Level.chunkHash(50, 50)));
    }

    @Test
    void tenFailedReadsDoNotBlockHealthyChunks() throws Exception {
        Level level = mock(Level.class);
        Server server = mock(Server.class);
        server.chunksPerTick = 10;
        server.asyncChunkLoadCompletion = true;
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        when(level.isAsyncChunkLoadEnabled()).thenReturn(true);
        when(level.requestChunkLoadAsync(anyInt(), anyInt())).thenReturn(true);
        Player player = player(level, server);
        for (int i = 0; i < 10; i++) player.loadQueue.add(Level.chunkHash(i, 0));
        BaseFullChunk healthy = mock(BaseFullChunk.class);
        when(healthy.isPopulated()).thenReturn(true);
        when(level.getChunkIfLoaded(50, 50)).thenReturn(healthy);
        when(level.populateChunk(50, 50)).thenReturn(true);
        player.loadQueue.add(Level.chunkHash(50, 50));
        for (int tick = 0; tick < 200; tick++) player.sendNextChunk();
        verify(level).requestChunk(eq(50), eq(50), any());
    }

    @Test
    void nineQuarantinedChunksStillLetTheTenthSlotThrough() throws Exception {
        Level level = mock(Level.class);
        Server server = mock(Server.class);
        server.chunksPerTick = 10;
        server.asyncChunkLoadCompletion = true;
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        when(level.isAsyncChunkLoadEnabled()).thenReturn(true);
        Player player = player(level, server);
        for (int i = 0; i < 9; i++) {
            BaseFullChunk bad = mock(BaseFullChunk.class);
            when(bad.isReadFailurePlaceholder()).thenReturn(true);
            when(level.getChunkIfLoaded(i, 0)).thenReturn(bad);
            player.loadQueue.add(Level.chunkHash(i, 0));
        }
        BaseFullChunk healthy = mock(BaseFullChunk.class);
        when(healthy.isPopulated()).thenReturn(true);
        when(level.getChunkIfLoaded(50, 50)).thenReturn(healthy);
        when(level.populateChunk(50, 50)).thenReturn(true);
        player.loadQueue.add(Level.chunkHash(50, 50));
        player.sendNextChunk();
        verify(level).requestChunk(eq(50), eq(50), any());
    }
    @Test
    void lateQuarantineCannotReachTheNetworkOrCountAsDelivered() throws Exception {
        Level level = mock(Level.class);
        Server server = mock(Server.class);
        Player player = player(level, server);
        BaseFullChunk failed = mock(BaseFullChunk.class);
        when(failed.isReadFailurePlaceholder()).thenReturn(true);
        when(level.getChunkIfLoaded(3, 5)).thenReturn(failed);
        doCallRealMethod().when(player).sendChunk(anyInt(), anyInt(), any(cn.nukkit.network.protocol.DataPacket.class));
        player.sendChunk(3, 5, mock(cn.nukkit.network.protocol.DataPacket.class));
        assertTrue(player.loadQueue.contains(Level.chunkHash(3, 5)));
        assertNotEquals(Boolean.TRUE, player.usedChunks.get(Level.chunkHash(3, 5)));
        verify(player, never()).dataPacket(any(cn.nukkit.network.protocol.DataPacket.class));
    }

    @Test
    void healthyChunksStillRespectTheSendingQuotaAfterQuarantine() throws Exception {
        Level level = mock(Level.class);
        Server server = mock(Server.class);
        server.chunksPerTick = 2;
        server.asyncChunkLoadCompletion = true;
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        when(level.isAsyncChunkLoadEnabled()).thenReturn(true);
        Player player = player(level, server);
        for (int i = 0; i < 12; i++) {
            BaseFullChunk bad = mock(BaseFullChunk.class);
            when(bad.isReadFailurePlaceholder()).thenReturn(true);
            when(level.getChunkIfLoaded(i, 0)).thenReturn(bad);
            player.loadQueue.add(Level.chunkHash(i, 0));
        }
        for (int i = 50; i < 55; i++) {
            BaseFullChunk healthy = mock(BaseFullChunk.class);
            when(healthy.isPopulated()).thenReturn(true);
            when(level.getChunkIfLoaded(i, 0)).thenReturn(healthy);
            when(level.populateChunk(i, 0)).thenReturn(true);
            player.loadQueue.add(Level.chunkHash(i, 0));
        }
        player.sendNextChunk();
        verify(level).requestChunk(eq(50), eq(0), any());
        verify(level).requestChunk(eq(51), eq(0), any());
        verify(level, never()).requestChunk(eq(52), eq(0), any());
    }

    @Test
    void quarantineNeighbourCannotStarveSynchronousSending() throws Exception {
        Level level = mock(Level.class);
        Server server = mock(Server.class);
        server.chunksPerTick = 10;
        server.asyncChunkLoadCompletion = true;
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        Player player = player(level, server);
        BaseFullChunk waiting = mock(BaseFullChunk.class);
        BaseFullChunk failed = mock(BaseFullChunk.class);
        when(failed.isReadFailurePlaceholder()).thenReturn(true);
        when(level.getChunkIfLoaded(0, 0)).thenReturn(waiting);
        when(level.getChunkIfLoaded(1, 0)).thenReturn(failed);
        when(level.populateChunk(0, 0)).thenReturn(false);
        when(level.populateChunk(50, 50)).thenReturn(true);
        player.loadQueue.add(Level.chunkHash(0, 0));
        player.loadQueue.add(Level.chunkHash(50, 50));
        player.sendNextChunk();
        verify(level).requestChunk(eq(50), eq(50), any());
    }

}
