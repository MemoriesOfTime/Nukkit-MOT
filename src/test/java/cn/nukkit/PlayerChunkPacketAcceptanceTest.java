package cn.nukkit;

import cn.nukkit.level.Level;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.LevelChunkPacket;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerChunkPacketAcceptanceTest {
    private final long index = Level.chunkHash(3, 5);
    private Player player;
    private Level level;
    private DataPacket packet;

    @BeforeEach
    void initialize() throws Exception {
        player = mock(Player.class);
        level = mock(Level.class);
        packet = new LevelChunkPacket();
        player.level = level;
        player.connected = true;
        player.protocol = 0;
        field("usedChunks", new Long2ObjectOpenHashMap<Boolean>());
        field("loadQueue", new LongLinkedOpenHashSet());
        player.usedChunks.put(index, Boolean.FALSE);
        doCallRealMethod().when(player).sendChunk(anyInt(), anyInt(), any(DataPacket.class));
    }

    private void field(String name, Object value) throws Exception {
        Field field = Player.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(player, value);
    }

    private int sent() throws Exception {
        Field field = Player.class.getDeclaredField("chunksSent");
        field.setAccessible(true);
        return field.getInt(player);
    }

    @Test
    void usedChunksKeepsItsPublishedFieldDescriptor() throws Exception {
        assertEquals(java.util.Map.class, Player.class.getField("usedChunks").getType(),
                "existing plugins link to usedChunks with the java.util.Map descriptor");
    }

    @Test
    void rejectedSubmissionKeepsTerrainPendingAndRetriesThroughExistingQueue() throws Exception {
        when(player.dataPacket(packet)).thenReturn(false);
        player.sendChunk(3, 5, packet);
        assertFalse(player.usedChunks.get(index));
        assertTrue(player.loadQueue.contains(index));
        assertEquals(0, sent());
    }

    @Test
    void acceptedSubmissionMarksReadyOnlyAfterPacketAdmission() throws Exception {
        doAnswer(call -> {
            assertFalse(player.usedChunks.get(index), "readiness precedes transport admission");
            return true;
        }).when(player).dataPacket(packet);
        player.sendChunk(3, 5, packet);
        assertTrue(player.usedChunks.get(index));
        assertEquals(1, sent());
        assertTrue(player.loadQueue.isEmpty());
    }

    @Test
    void throwingSubmissionPreservesCauseAndSchedulesTrackedRetry() throws Exception {
        RuntimeException failure = new IllegalStateException("packet listener failure");
        doThrow(failure).when(player).dataPacket(packet);
        assertSame(failure, assertThrows(RuntimeException.class, () -> player.sendChunk(3, 5, packet)));
        assertFalse(player.usedChunks.get(index));
        assertTrue(player.loadQueue.contains(index));
        assertEquals(0, sent());
    }

    @Test
    void packetCallbackChangingWorldCannotMarkTheOldWorldReady() throws Exception {
        Level replacement = mock(Level.class);
        doAnswer(call -> {
            player.level = replacement;
            player.usedChunks.clear();
            return true;
        }).when(player).dataPacket(packet);
        player.sendChunk(3, 5, packet);
        assertFalse(player.usedChunks.containsKey(index));
        assertTrue(player.loadQueue.isEmpty());
        assertEquals(0, sent());
    }

    @Test
    void sameWorldCleanupAndResubscriptionInvalidateTheOldCallback() throws Exception {
        Method cleanup = Player.class.getDeclaredMethod("unloadChunks", boolean.class);
        cleanup.setAccessible(true);
        doAnswer(call -> {
            cleanup.invoke(player, false);
            player.usedChunks.put(index, Boolean.FALSE);
            return true;
        }).when(player).dataPacket(packet);
        player.sendChunk(3, 5, packet);
        assertFalse(player.usedChunks.get(index));
        assertEquals(0, sent());
    }

    @Test
    void rejectedUntrackedOrDisconnectedPacketsDoNotCreateRetries() throws Exception {
        player.usedChunks.clear();
        player.sendChunk(3, 5, packet);
        assertFalse(player.usedChunks.containsKey(index));
        assertTrue(player.loadQueue.isEmpty());
        player.connected = false;
        player.usedChunks.put(index, Boolean.FALSE);
        player.sendChunk(3, 5, packet);
        assertFalse(player.usedChunks.get(index));
        assertTrue(player.loadQueue.isEmpty());
        assertEquals(0, sent());
    }
    @Test
    void rejectedOuterSubmissionPreservesAcceptedReentrantReplacement() throws Exception {
        DataPacket replacement = new LevelChunkPacket();
        when(player.dataPacket(replacement)).thenReturn(true);
        doAnswer(call -> {
            player.sendChunk(3, 5, replacement);
            return false;
        }).when(player).dataPacket(packet);
        player.sendChunk(3, 5, packet);
        assertTrue(player.usedChunks.get(index));
        assertTrue(player.loadQueue.isEmpty());
        assertEquals(1, sent());
    }

    @Test
    void throwingOuterSubmissionPreservesAcceptedReentrantReplacement() throws Exception {
        DataPacket replacement = new LevelChunkPacket();
        RuntimeException failure = new IllegalStateException("listener failure after replacement");
        when(player.dataPacket(replacement)).thenReturn(true);
        doAnswer(call -> {
            player.sendChunk(3, 5, replacement);
            throw failure;
        }).when(player).dataPacket(packet);
        assertSame(failure, assertThrows(RuntimeException.class, () -> player.sendChunk(3, 5, packet)));
        assertTrue(player.usedChunks.get(index));
        assertTrue(player.loadQueue.isEmpty());
        assertEquals(1, sent());
    }

}
