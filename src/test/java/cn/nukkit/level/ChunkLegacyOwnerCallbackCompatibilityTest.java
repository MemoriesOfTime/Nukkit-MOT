package cn.nukkit.level;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.generic.serializer.NetworkChunkSerializer.NetworkChunkSerializerCallbackData;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalMatchers.aryEq;

class ChunkLegacyOwnerCallbackCompatibilityTest {
    private static final GameVersion VERSION = GameVersion.V1_20_0;
    private static final long INDEX = Level.chunkHash(3, 5);

    @BeforeAll static void initialize() { MockServer.init(); }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void existingOwnerOverloadStillInvokesTheNumericVirtualHook(boolean async) throws Exception {
        Fixture f = new Fixture();
        f.complete(async, 1, f.owner, new byte[]{11});
        assertEquals(1, f.level.hooks);
        f.drain();
        assertFalse(f.tasks().contains(INDEX));
        verify(f.player).sendChunk(eq(3), eq(5), eq(1), aryEq(new byte[]{11}), eq(0));
    }

    @ParameterizedTest @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void delegatedSuperRetainsOuterOwnerAcrossReplacementAndNestedOtherLevel(boolean async, boolean zero) throws Exception {
        Fixture f = new Fixture();
        Fixture nested = new Fixture();
        Level.ChunkRequestOwner outer = f.owner;
        f.level.beforeSuper = () -> {
            f.level.beforeSuper = null;
            try {
                f.player.usedChunks.clear();
                f.level.requestChunk(3, 5, f.player);
                f.process();
                f.player.usedChunks.put(INDEX, false);
                f.level.requestChunk(3, 5, f.player);
                f.process();
                f.owner = f.capture();
                assertNotSame(outer, f.owner);
                nested.complete(async, 1, nested.owner, new byte[]{31});
            } catch (Exception e) { throw new AssertionError(e); }
        };
        f.complete(async, zero ? 0 : 1, outer, new byte[]{11});
        f.drain();
        nested.drain();
        assertEquals(1, f.level.hooks, "the old public virtual hook remains visible exactly once");
        assertTrue(f.tasks().contains(INDEX), "outer super must retain the replacement even with exempt numeric epoch zero");
        verify(f.player, never()).sendChunk(anyInt(), anyInt(), anyInt(), any(byte[].class), anyInt());
        verify(nested.player).sendChunk(eq(3), eq(5), eq(1), aryEq(new byte[]{31}), eq(0));
        f.complete(async, 2, f.owner, new byte[]{21});
        f.drain();
        verify(f.player).sendChunk(eq(3), eq(5), eq(1), aryEq(new byte[]{21}), eq(0));
        assertFalse(f.tasks().contains(INDEX));
    }

    static class LegacyLevel extends Level {
        int hooks;
        Runnable beforeSuper;
        LegacyLevel() { super(null, null, null, null); }

        @Override public void chunkRequestCallback(GameVersion version, long timestamp, int x, int z,
                int count, byte[] payload, List<byte[]> blobs, byte[] biomes, byte[] entities, long sequence) {
            hooks++;
            if (beforeSuper != null) beforeSuper.run();
            super.chunkRequestCallback(version, timestamp, x, z, count, payload, blobs, biomes, entities, sequence);
        }
        @Override public void asyncChunkRequestCallback(GameVersion version, long timestamp, int x, int z,
                int count, byte[] payload, List<byte[]> blobs, byte[] biomes, byte[] entities, long sequence) {
            hooks++;
            if (beforeSuper != null) beforeSuper.run();
            super.asyncChunkRequestCallback(version, timestamp, x, z, count, payload, blobs, biomes, entities, sequence);
        }
    }

    private static final class Fixture {
        final LegacyLevel level = mock(LegacyLevel.class, CALLS_REAL_METHODS);
        final Server server = mock(Server.class);
        final Player player = mock(Player.class);
        Level.ChunkRequestOwner owner;
        Fixture() throws Exception {
            set(level, "server", server);
            set(level, "dimensionData", DimensionData.LEGACY_DIMENSION);
            when(server.getTick()).thenReturn(1);
            set(level, "provider", mock(LevelProvider.class));
            for (String field : new String[]{"chunkSendQueues", "chunkSendTasks", "pendingChunkRequests", "chunkRequestEpochs"})
                set(level, field, new Object2ObjectOpenHashMap<>());
            set(level, "chunkSendTaskStartTick", new Long2LongOpenHashMap());
            set(level, "asyncChunkRequestCallbackQueue", new ConcurrentLinkedQueue<>());
            BaseFullChunk source = mock(BaseFullChunk.class);
            doReturn(source).when(level).getChunk(3, 5);
            doReturn(source).when(level).getChunkIfLoaded(3, 5);
            set(player, "usedChunks", new HashMap<Long, Boolean>());
            player.usedChunks.put(INDEX, false);
            when(player.getLoaderId()).thenReturn(1);
            when(player.getGameVersion()).thenReturn(VERSION);
            when(player.getLevel()).thenReturn(level);
            when(player.isConnected()).thenReturn(true);
            level.requestChunk(3, 5, player);
            process();
            owner = capture();
        }
        void process() throws Exception {
            Method m = Level.class.getDeclaredMethod("processChunkRequest"); m.setAccessible(true); m.invoke(level);
        }
        Level.ChunkRequestOwner capture() { return level.captureChunkRequestOwner(new ObjectOpenHashSet<>(List.of(VERSION)), 3, 5); }
        LongSet tasks() throws Exception {
            Method m = Level.class.getDeclaredMethod("getChunkSendTasks", GameVersion.class); m.setAccessible(true); return (LongSet) m.invoke(level, VERSION);
        }
        void complete(boolean async, long sequence, Level.ChunkRequestOwner owner, byte[] payload) {
            if (async) level.asyncChunkRequestCallback(VERSION, 7, 3, 5, 1, payload, null, null, null, sequence, owner);
            else level.chunkRequestCallback(VERSION, 7, 3, 5, 1, payload, null, null, null, sequence, owner);
        }
        void drain() throws Exception {
            Field f = Level.class.getDeclaredField("asyncChunkRequestCallbackQueue"); f.setAccessible(true);
            @SuppressWarnings("unchecked")
            Queue<NetworkChunkSerializerCallbackData> queue =
                    (Queue<NetworkChunkSerializerCallbackData>) f.get(level);
            NetworkChunkSerializerCallbackData data;
            while ((data = queue.poll()) != null) {
                if (level.isSupersededChunkCallback(data)) continue;
                level.chunkRequestCallback(data.getGameVersion(), data.getTimestamp(), data.getX(), data.getZ(),
                        data.getSubChunkCount(), data.getPayload(), data.getSubChunkBlobs(), data.getBiomes(),
                        data.getBlockEntities(), data.getSequence(), data.getRequestOwner());
            }
        }
    }
    private static void set(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { Field f = type.getDeclaredField(name); f.setAccessible(true); f.set(target, value); return; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
}
