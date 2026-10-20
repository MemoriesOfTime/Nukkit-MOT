package cn.nukkit.level;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.generic.serializer.ChunkRequestToken;
import cn.nukkit.level.format.generic.serializer.NetworkChunkSerializer;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.network.protocol.BatchPacket;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChunkCacheSnapshotValidationTest {
    private static final GameVersion VERSION = GameVersion.V1_20_0;
    private Level level;
    private LevelDBProvider provider;
    private LevelDBChunk original;
    private Player player;
    private ChunkRequestToken token;
    private AtomicReference<BaseFullChunk> mounted;
    private final byte[] payload = {1, 2, 3};
    private final BatchPacket encoded = new BatchPacket();

    @BeforeAll static void init() { MockServer.init(); }

    @BeforeEach void setup() throws Exception {
        level = mock(Level.class, CALLS_REAL_METHODS);
        Server server = mock(Server.class);
        server.cacheChunks = true;
        set(level, Level.class, "server", server);
        for (String name : new String[]{"chunkSendQueues", "chunkSendTasks", "pendingChunkRequests"}) {
            set(level, Level.class, name, new Object2ObjectOpenHashMap<>());
        }
        set(level, Level.class, "chunkSendTaskStartTick", new Long2LongOpenHashMap());
        set(level, Level.class, "asyncChunkRequestCallbackQueue", new ConcurrentLinkedQueue<>());
        doReturn(0).when(level).getDimension();
        doReturn(new DimensionData(0, 0, 15)).when(level).getDimensionData();
        provider = mock(LevelDBProvider.class);
        when(provider.getLevel()).thenReturn(level);
        when(provider.getMinBlockY()).thenReturn(0);
        when(provider.getMaxBlockY()).thenReturn(15);
        original = newChunk();
        original.setBlock(1, 1, 1, BlockID.STONE);
        original.setChanged(false);
        for (int i = 0; i < 7; i++) original.setChanged();
        token = new ChunkRequestToken(original);
        mounted = new AtomicReference<>(original);
        doAnswer(ignored -> mounted.get()).when(level).getChunkIfLoaded(3, 5);
        player = mock(Player.class);
        set(player, Player.class, "usedChunks", new HashMap<Long, Boolean>());
        player.usedChunks.put(Level.chunkHash(3, 5), false);
        when(player.getLoaderId()).thenReturn(1);
        when(player.getGameVersion()).thenReturn(VERSION);
        when(player.getLevel()).thenReturn(level);
        when(player.isConnected()).thenReturn(true);
        level.requestChunk(3, 5, player);
        var tasks = Level.class.getDeclaredMethod("getChunkSendTasks", GameVersion.class);
        tasks.setAccessible(true);
        ((LongSet) tasks.invoke(level, VERSION)).add(Level.chunkHash(3, 5));
    }

    private LevelDBChunk newChunk() { return LevelDBChunk.getEmptyChunk(3, 5, provider); }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void dirtyCounterResetCannotPublishAnOlderBlockSnapshot(boolean async) throws Exception {
        long timestamp = original.getChanges();
        assertEquals(7, timestamp);
        original.setBlock(1, 1, 1, BlockID.DIAMOND_BLOCK);
        assertEquals(8, original.getChanges());
        assertTrue(original.clearChangesIfUnmodified(8), "model a successful durable save");
        assertEquals(0, original.getChanges());
        deliver(timestamp, async, token);
        assertNull(original.getChunkPacket(VERSION), "old block snapshot must not become persistent cache");
        assertEquals(BlockID.DIAMOND_BLOCK, original.getBlockId(1, 1, 1));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void replacementChunkCannotReceiveThePreviousOwnersPacket(boolean async) throws Exception {
        long timestamp = original.getChanges();
        LevelDBChunk replacement = newChunk();
        while (replacement.getMutationRevision() < original.getMutationRevision()) replacement.setChanged();
        assertEquals(original.getMutationRevision(), replacement.getMutationRevision());
        replacement.setChanged(false);
        mounted.set(replacement);
        deliver(timestamp, async, token);
        assertNull(replacement.getChunkPacket(VERSION), "matching coordinates are not chunk identity");
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void saveWithoutMutationKeepsTheCapturedPacketCacheable(boolean async) throws Exception {
        long timestamp = original.getChanges();
        assertTrue(original.clearChangesIfUnmodified(timestamp));
        deliver(timestamp, async, token);
        assertSame(encoded, original.getChunkPacket(VERSION));
        verify(player).sendChunk(3, 5, encoded);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void liveMutationWithoutSavingAlsoRejectsTheOldCache(boolean async) throws Exception {
        long timestamp = original.getChanges();
        original.setBlock(1, 1, 1, BlockID.DIAMOND_BLOCK);
        deliver(timestamp, async, token);
        assertNull(original.getChunkPacket(VERSION));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void unloadBeforeCompletionDoesNotResurrectACache(boolean async) throws Exception {
        mounted.set(null);
        deliver(original.getChanges(), async, token);
        assertNull(original.getChunkPacket(VERSION));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void legacyProviderCallbacksStillDeliverWithoutPublishingAnUnverifiableCache(boolean async) throws Exception {
        deliver(original.getChanges(), async, null);
        assertNull(original.getChunkPacket(VERSION));
        verify(player).sendChunk(3, 5, encoded);
    }

    @Test void changingACloneDoesNotChangeTheLiveRevision() {
        long revision = original.getMutationRevision();
        BaseFullChunk copy = original.clone();
        copy.setChanged();
        assertEquals(revision, original.getMutationRevision());
        assertTrue(token.matches(original));
        assertFalse(token.matches(copy));
    }

    @Test void changingANetworkCloneDoesNotChangeTheLiveRevision() {
        long revision = original.getMutationRevision();
        BaseFullChunk copy = original.cloneForChunkSending();
        copy.setChanged();
        assertEquals(revision, original.getMutationRevision());
        assertTrue(token.matches(original));
        assertFalse(token.matches(copy));
    }

    private void deliver(long timestamp, boolean async, ChunkRequestToken captured) throws Exception {
        try (var packets = mockStatic(Player.class)) {
            packets.when(() -> Player.getChunkCacheFromData(VERSION, 3, 5, 1, payload, 0)).thenReturn(encoded);
            if (async) {
                if (captured == null) level.asyncChunkRequestCallback(VERSION, timestamp, 3, 5, 1, payload);
                else level.asyncChunkRequestCallback(VERSION, timestamp, 3, 5, 1, payload, captured);
                Field queueField = Level.class.getDeclaredField("asyncChunkRequestCallbackQueue");
                queueField.setAccessible(true);
                Object prepared = ((Queue<?>) queueField.get(level)).remove();
                var dataAccessor = prepared.getClass().getDeclaredMethod("data");
                var tokenAccessor = prepared.getClass().getDeclaredMethod("token");
                dataAccessor.setAccessible(true);
                tokenAccessor.setAccessible(true);
                var data = (NetworkChunkSerializer.NetworkChunkSerializerCallbackData) dataAccessor.invoke(prepared);
                assertSame(captured, tokenAccessor.invoke(prepared));
                level.chunkRequestCallback(VERSION, timestamp, 3, 5, 1, data.getPayload(),
                        (ChunkRequestToken) tokenAccessor.invoke(prepared));
            } else if (captured == null) {
                level.chunkRequestCallback(VERSION, timestamp, 3, 5, 1, payload);
            } else {
                level.chunkRequestCallback(VERSION, timestamp, 3, 5, 1, payload, captured);
            }
        }
    }

    private static void set(Object target, Class<?> owner, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
