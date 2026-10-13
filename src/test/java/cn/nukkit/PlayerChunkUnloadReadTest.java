package cn.nukkit;

import cn.nukkit.entity.Entity;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.RemoveEntityPacket;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlayerChunkUnloadReadTest {
    private Player player;
    private Level level;
    private final long hash = Level.chunkHash(3, 5);

    @BeforeEach
    void setUp() throws Exception {
        player = mock(Player.class);
        level = loadedOnlyLevel();
        player.level = level;
        set(Player.class, player, "loaderId", 7);
        when(player.getLoaderId()).thenReturn(7);
        set(Player.class, player, "usedChunks", new Long2ObjectOpenHashMap<>());
        set(Player.class, player, "loadQueue", new LongLinkedOpenHashSet());
        player.usedChunks.put(hash, true);
        player.loadQueue.add(hash);
        doCallRealMethod().when(player).unloadChunk(anyInt(), anyInt(), nullable(Level.class));
    }

    private Level loadedOnlyLevel() {
        Level result = mock(Level.class);
        when(result.getChunkEntities(anyInt(), anyInt())).thenCallRealMethod();
        when(result.getChunkEntities(anyInt(), anyInt(), anyBoolean())).thenCallRealMethod();
        return result;
    }

    @Test
    void abandoningColdChunkDoesNotReadStorageAndStillClearsTracking() {
        player.unloadChunk(3, 5, null);
        verify(level, never()).getChunk(anyInt(), anyInt());
        verify(level).getChunkIfLoaded(3, 5);
        verify(level).unregisterChunkLoader(player, 3, 5);
        assertFalse(player.usedChunks.containsKey(hash));
        assertFalse(player.loadQueue.contains(hash));
    }

    @Test
    void loadedChunkDespawnsBeforeUnregisterAndSkipsPlayerItself() {
        Entity entity = mock(Entity.class);
        BaseFullChunk chunk = mock(BaseFullChunk.class);
        when(level.getChunkIfLoaded(3, 5)).thenReturn(chunk);
        when(level.getChunk(3, 5)).thenReturn(chunk);
        when(chunk.getEntities()).thenReturn(Map.of(1L, entity, 2L, player));
        doAnswer(invocation -> {
            assertTrue(player.usedChunks.containsKey(hash));
            return null;
        }).when(entity).despawnFrom(player);
        doAnswer(invocation -> {
            assertFalse(player.usedChunks.containsKey(hash));
            assertTrue(player.loadQueue.contains(hash));
            return null;
        }).when(level).unregisterChunkLoader(player, 3, 5);
        player.unloadChunk(3, 5, null);
        var order = inOrder(entity, level);
        order.verify(entity).despawnFrom(player);
        order.verify(level).unregisterChunkLoader(player, 3, 5);
        verify(player, never()).despawnFrom(any());
        verify(level, never()).getChunk(anyInt(), anyInt());
    }

    @Test
    void explicitOldLevelOwnsLookupAndUnregister() {
        Level oldLevel = loadedOnlyLevel();
        player.unloadChunk(3, 5, oldLevel);
        verify(oldLevel).getChunkIfLoaded(3, 5);
        verify(oldLevel).unregisterChunkLoader(player, 3, 5);
        verify(oldLevel, never()).getChunk(anyInt(), anyInt());
        verifyNoInteractions(level);
    }

    @Test
    void worldSwitchAndDisconnectNeverReadColdChunks() throws Exception {
        for (boolean online : new boolean[]{true, false}) {
            player.usedChunks.put(hash, true);
            player.loadQueue.add(hash);
            unloadAll(online);
            assertTrue(player.usedChunks.isEmpty());
            assertTrue(player.loadQueue.isEmpty());
        }
        verify(level, never()).getChunk(anyInt(), anyInt());
        verify(level, times(2)).unregisterChunkLoader(player, 3, 5);
    }

    @Test
    void onlineCleanupKeepsEntityPacketAndOfflineCleanupSendsNothing() throws Exception {
        Entity entity = mock(Entity.class);
        set(Entity.class, entity, "hasSpawned", new ConcurrentHashMap<Integer, Player>());
        set(Entity.class, entity, "id", 123L);
        doCallRealMethod().when(entity).despawnFrom(player);
        BaseFullChunk chunk = mock(BaseFullChunk.class);
        when(level.getChunkIfLoaded(3, 5)).thenReturn(chunk);
        when(level.getChunk(3, 5)).thenReturn(chunk);
        when(chunk.getEntities()).thenReturn(Map.of(123L, entity));
        int protocols = 0;
        for (int protocol : ProtocolInfo.SUPPORTED_PROTOCOLS) {
            if (protocol < ProtocolInfo.v1_20_0 || protocol > ProtocolInfo.v1_26_50) continue;
            player.protocol = protocol;
            player.usedChunks.put(hash, true);
            entity.hasSpawned.put(7, player);
            unloadAll(true);
            assertFalse(entity.hasSpawned.containsKey(7));
            protocols++;
        }
        verify(player, times(protocols)).dataPacket(argThat(packet ->
                packet instanceof RemoveEntityPacket remove && remove.eid == 123L));
        clearInvocations(player, entity, level);
        player.usedChunks.put(hash, true);
        entity.hasSpawned.put(7, player);
        unloadAll(false);
        assertFalse(entity.hasSpawned.containsKey(7));
        verify(player, never()).dataPacket(any());
        verify(entity, never()).despawnFrom(any());
        verify(level, never()).getChunk(anyInt(), anyInt());
    }

    private void unloadAll(boolean online) throws Exception {
        var method = Player.class.getDeclaredMethod("unloadChunks", boolean.class);
        method.setAccessible(true);
        method.invoke(player, online);
    }

    private static void set(Class<?> type, Object target, String name, Object value) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
