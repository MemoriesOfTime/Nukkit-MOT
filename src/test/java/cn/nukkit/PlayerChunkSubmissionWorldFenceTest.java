package cn.nukkit;

import cn.nukkit.event.Event;
import cn.nukkit.event.server.DataPacketSendEvent;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.network.protocol.BatchPacket;
import cn.nukkit.network.protocol.ChangeDimensionPacket;
import cn.nukkit.network.protocol.ChunkRadiusUpdatedPacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.LevelChunkPacket;
import cn.nukkit.network.protocol.SubChunkPacket;
import cn.nukkit.network.session.NetworkPlayerSession;
import cn.nukkit.plugin.PluginManager;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlayerChunkSubmissionWorldFenceTest {
    @Test
    void nestedFreshChunkAndControlPacketsSurviveWhileOuterOldChunkIsRejected() throws Exception {
        Player player = mock(Player.class);
        Level original = mock(Level.class), destination = mock(Level.class);
        Server server = mock(Server.class);
        PluginManager plugins = mock(PluginManager.class);
        NetworkPlayerSession network = mock(NetworkPlayerSession.class);
        field(player, "usedChunks", new Long2ObjectOpenHashMap<Boolean>());
        field(player, "loadQueue", new LongLinkedOpenHashSet());
        field(player, "server", server);
        field(player, "networkSession", network);
        player.level = original;
        player.connected = true;
        player.usedChunks.put(Level.chunkHash(3, 5), false);
        server.callDataPkSendEv = true;
        when(server.getPluginManager()).thenReturn(plugins);
        when(original.getChunkEntities(anyInt(), anyInt(), eq(false))).thenReturn(Map.of());
        doCallRealMethod().when(player).sendChunk(anyInt(), anyInt(), any(DataPacket.class));
        doCallRealMethod().when(player).dataPacket(any(DataPacket.class));
        doCallRealMethod().when(player).preSwitchLevel();
        AtomicInteger chunkEvents = new AtomicInteger();
        doAnswer(call -> {
            Event event = call.getArgument(0);
            if (event instanceof DataPacketSendEvent send && send.getPacket() instanceof LevelChunkPacket
                    && chunkEvents.getAndIncrement() == 0) {
                player.preSwitchLevel();
                player.level = destination;
                player.dataPacket(new ChangeDimensionPacket());
                player.usedChunks.put(Level.chunkHash(9, 9), false);
                LevelChunkPacket fresh = new LevelChunkPacket();
                fresh.chunkX = 9;
                fresh.chunkZ = 9;
                player.sendChunk(9, 9, fresh);
                player.dataPacket(new ChunkRadiusUpdatedPacket());
            }
            return null;
        }).when(plugins).callEvent(any(Event.class));
        LevelChunkPacket old = new LevelChunkPacket();
        old.chunkX = 3;
        old.chunkZ = 5;

        player.sendChunk(3, 5, old);

        ArgumentCaptor<DataPacket> admitted = ArgumentCaptor.forClass(DataPacket.class);
        verify(network, times(3)).sendPacket(admitted.capture());
        assertInstanceOf(ChangeDimensionPacket.class, admitted.getAllValues().get(0));
        LevelChunkPacket fresh = assertInstanceOf(LevelChunkPacket.class, admitted.getAllValues().get(1));
        assertEquals(9, fresh.chunkX);
        assertEquals(9, fresh.chunkZ);
        assertInstanceOf(ChunkRadiusUpdatedPacket.class, admitted.getAllValues().get(2));
        assertEquals(Boolean.TRUE, player.usedChunks.get(Level.chunkHash(9, 9)));
        assertFalse(player.usedChunks.containsKey(Level.chunkHash(3, 5)));
    }

    @ParameterizedTest
    @CsvSource({"RAW,false", "BATCH,false", "RAW,true", "BATCH,true", "SUB,false", "SUB,true"})
    void oldTerrainNeverReachesNetworkAfterListenerChangesWorldOrEpoch(String packetKind, boolean sameWorld) throws Exception {
        Player player = mock(Player.class);
        Level original = mock(Level.class), destination = mock(Level.class);
        Server server = mock(Server.class);
        PluginManager plugins = mock(PluginManager.class);
        NetworkPlayerSession network = mock(NetworkPlayerSession.class);
        field(player, "usedChunks", new Long2ObjectOpenHashMap<Boolean>());
        field(player, "loadQueue", new LongLinkedOpenHashSet());
        field(player, "server", server);
        field(player, "networkSession", network);
        field(player, "gameVersion", GameVersion.V1_21_70);
        player.level = original;
        player.connected = true;
        player.protocol = GameVersion.V1_21_70.getProtocol();
        player.usedChunks.put(Level.chunkHash(3, 5), false);
        server.callDataPkSendEv = true;
        when(server.getPluginManager()).thenReturn(plugins);
        when(original.getChunkIfLoaded(3, 5)).thenReturn(mock(BaseFullChunk.class));
        when(original.getChunkEntities(anyInt(), anyInt(), eq(false))).thenReturn(Map.of());
        doCallRealMethod().when(player).sendChunk(anyInt(), anyInt(), any(DataPacket.class));
        doCallRealMethod().when(player).dataPacket(any(DataPacket.class));
        doCallRealMethod().when(player).preSwitchLevel();
        doAnswer(call -> {
            Event event = call.getArgument(0);
            if (event instanceof DataPacketSendEvent) {
                player.preSwitchLevel();
                if (!sameWorld) player.level = destination;
            }
            return null;
        }).when(plugins).callEvent(any(Event.class));

        if (packetKind.equals("SUB")) player.dataPacket(new SubChunkPacket());
        else player.sendChunk(3, 5, packetKind.equals("BATCH") ? new BatchPacket() : new LevelChunkPacket());

        verify(network, never()).sendPacket(any(DataPacket.class));
        assertTrue(player.usedChunks.isEmpty());
        assertTrue(player.loadQueue.isEmpty());
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
