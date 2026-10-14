package cn.nukkit;

import cn.nukkit.level.Level;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.plugin.PluginManager;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlayerChunkReadBackpressureTest {
    private Player player;
    private Level level;
    private final long hash = Level.chunkHash(3, 5);

    @BeforeEach
    void setUp() throws Exception {
        player = mock(Player.class);
        level = mock(Level.class);
        Server server = mock(Server.class);
        server.chunksPerTick = 10;
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        set(cn.nukkit.entity.Entity.class, player, "server", server);
        player.level = level;
        player.connected = true;
        set(Player.class, player, "hasSpawnChunks", true);
        set(Player.class, player, "usedChunks", new Long2ObjectOpenHashMap<>());
        set(Player.class, player, "loadQueue", new LongLinkedOpenHashSet());
        player.loadQueue.add(hash);
        doCallRealMethod().when(player).sendNextChunk();
        when(level.isAsyncChunkLoadEnabled()).thenReturn(true);
    }

    private static void set(Class<?> type, Object target, String name, Object value) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void rejectedColdReadWaitsWithoutSynchronousPopulationAndRetries() {
        when(level.requestChunkLoadAsync(3, 5)).thenReturn(false, true);
        player.sendNextChunk();
        player.sendNextChunk();
        verify(level, times(2)).requestChunkLoadAsync(3, 5);
        verify(level, never()).populateChunk(anyInt(), anyInt());
        verify(level, never()).requestChunk(anyInt(), anyInt(), any());
        assertTrue(player.loadQueue.contains(hash));
    }

    @Test
    void rejectedNeighbourReadNeverFallsThroughToSynchronousPopulation() {
        BaseFullChunk chunk = mock(BaseFullChunk.class);
        when(level.getChunkIfLoaded(3, 5)).thenReturn(chunk);
        when(level.requestChunkLoadAsync(2, 4)).thenReturn(false);
        player.sendNextChunk();
        verify(level, never()).populateChunk(anyInt(), anyInt());
        assertTrue(player.loadQueue.contains(hash));
    }

    @Test
    void readyChunkStillSendsWhileColdReadsAreRejected() {
        long readyHash = Level.chunkHash(4, 5);
        player.loadQueue.add(readyHash);
        BaseFullChunk ready = mock(BaseFullChunk.class);
        when(ready.isPopulated()).thenReturn(true);
        when(level.getChunkIfLoaded(4, 5)).thenReturn(ready);
        when(level.populateChunk(4, 5)).thenReturn(true);
        player.sendNextChunk();
        verify(level).requestChunk(4, 5, player);
        verify(level, never()).populateChunk(3, 5);
        assertTrue(player.loadQueue.contains(hash));
        assertFalse(player.loadQueue.contains(readyHash));
    }

    @Test
    void rejectedReadEventuallySendsWhenAsyncLoadCompletes() {
        player.sendNextChunk();
        BaseFullChunk ready = mock(BaseFullChunk.class);
        when(ready.isPopulated()).thenReturn(true);
        when(level.getChunkIfLoaded(3, 5)).thenReturn(ready);
        when(level.populateChunk(3, 5)).thenReturn(true);
        player.sendNextChunk();
        verify(level, times(1)).populateChunk(3, 5);
        verify(level).requestChunk(3, 5, player);
        assertTrue(player.loadQueue.isEmpty());
    }

    @Test
    void unsupportedAsyncProviderKeepsExistingSynchronousPath() {
        when(level.isAsyncChunkLoadEnabled()).thenReturn(false);
        when(level.populateChunk(3, 5)).thenReturn(true);
        player.sendNextChunk();
        verify(level).populateChunk(3, 5);
        verify(level).requestChunk(3, 5, player);
        assertTrue(player.loadQueue.isEmpty());
    }
}
