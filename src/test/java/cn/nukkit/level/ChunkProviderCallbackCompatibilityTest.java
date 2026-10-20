package cn.nukkit.level;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.anvil.Anvil;
import cn.nukkit.level.format.anvil.Chunk;
import cn.nukkit.level.format.generic.BaseChunk;
import cn.nukkit.level.format.generic.BaseLevelProvider;
import cn.nukkit.level.format.generic.serializer.ChunkRequestToken;
import cn.nukkit.level.format.generic.serializer.NetworkChunkSerializer;
import cn.nukkit.level.format.generic.serializer.NetworkChunkSerializer.NetworkChunkSerializerCallback;
import cn.nukkit.level.format.generic.serializer.NetworkChunkSerializer.NetworkChunkSerializerCallbackData;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.MainLogger;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChunkProviderCallbackCompatibilityTest {
    private static final GameVersion VERSION = GameVersion.V1_20_0;
    private static final int X = 3;
    private static final int Z = 5;
    private static final int SUBCHUNKS = 2;
    private static final long TIMESTAMP = 7;
    private static final byte[] PAYLOAD = {11, 12, 13, 14};
    private static final List<byte[]> BLOBS = List.of(new byte[]{21}, new byte[]{22});
    private static final byte[] BIOMES = {31, 32};
    private static final byte[] BLOCK_ENTITIES = {41, 42};
    private static final DimensionData DIMENSION = new DimensionData(0, 0, 31);

    enum ProviderKind { ANVIL, LEVELDB }

    @BeforeAll
    static void initializeServer() {
        MockServer.init();
    }

    static Stream<Arguments> hooks() {
        return Stream.of(ProviderKind.values()).flatMap(kind -> Stream.of(false, true)
                .flatMap(async -> Stream.of(InheritedLegacyHookLevel.class, StructuredHookLevel.class,
                        InheritedStructuredHookLevel.class, NewTokenHookLevel.class)
                        .map(type -> Arguments.of(kind, async, type))));
    }

    @ParameterizedTest @MethodSource("hooks")
    void mostSpecificSupportedHookRunsOnceAndSuperNeverRecurses(
            ProviderKind kind, boolean async, Class<? extends LegacyHookLevel> type) throws Exception {
        LegacyHookLevel level = mock(type, CALLS_REAL_METHODS);
        Fixture fixture = new Fixture(kind, async, level);
        fixture.serialize();
        boolean newest = level instanceof NewTokenHookLevel;
        boolean structured = level instanceof StructuredHookLevel;
        assertEquals(structured ? 0 : async ? 0 : 1, level.syncCalls);
        assertEquals(structured ? 0 : async ? 1 : 0, level.asyncCalls);
        if (structured) {
            StructuredHookLevel hook = (StructuredHookLevel) level;
            assertEquals(newest ? 0 : 1, hook.structuredCalls);
            if (!newest) {
                assertSame(BLOBS, hook.seenBlobs);
                assertSame(BIOMES, hook.seenBiomes);
            }
        }
        if (newest) {
            assertEquals(1, ((NewTokenHookLevel) level).newestCalls);
            fixture.assertToken(level.seenToken);
        }
        fixture.assertNoProviderFailure();
        if (async) {
            Object prepared = fixture.onlyPreparedCallback();
            NetworkChunkSerializerCallbackData data = preparedData(prepared);
            assertArrayEquals(PAYLOAD, data.getPayload());
            if (structured) assertSame(BLOBS, data.getSubChunkBlobs());
            else assertNull(data.getSubChunkBlobs());
            if (structured && !newest) {
                assertNull(field(prepared, "token"), "legacy structured API cannot prove cache source identity");
            } else {
                assertSame(level.seenToken, field(prepared, "token"));
            }
        }
    }

    @ParameterizedTest @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void directLegacyAndTokenCallsCompleteOnlyOnce(boolean async, boolean tokenAware) throws Exception {
        LegacyHookLevel level = mock(LegacyHookLevel.class, CALLS_REAL_METHODS);
        Fixture fixture = new Fixture(ProviderKind.ANVIL, async, level);
        ChunkRequestToken token = new ChunkRequestToken(fixture.source);
        if (async) {
            if (tokenAware) level.asyncChunkRequestCallback(VERSION, TIMESTAMP, X, Z, SUBCHUNKS, PAYLOAD, token);
            else level.asyncChunkRequestCallback(VERSION, TIMESTAMP, X, Z, SUBCHUNKS, PAYLOAD);
            fixture.onlyPreparedCallback();
        } else {
            if (tokenAware) level.chunkRequestCallback(VERSION, TIMESTAMP, X, Z, SUBCHUNKS, PAYLOAD, token);
            else level.chunkRequestCallback(VERSION, TIMESTAMP, X, Z, SUBCHUNKS, PAYLOAD);
            assertTrue(fixture.prepared.isEmpty());
        }
        assertEquals(tokenAware && async ? 1 : 0, level.asyncCalls);
        assertEquals(tokenAware && !async ? 1 : 0, level.syncCalls);
    }

    @ParameterizedTest
    @CsvSource({"ANVIL,false", "ANVIL,true", "LEVELDB,false", "LEVELDB,true"})
    void customLevelKeepsExistingTokenHookAndCanDelegateWithoutRecursion(
            ProviderKind kind, boolean async) throws Exception {
        LegacyHookLevel level = mock(LegacyHookLevel.class, CALLS_REAL_METHODS);
        Fixture fixture = new Fixture(kind, async, level);

        fixture.serialize();

        assertEquals(async ? 0 : 1, level.syncCalls, "existing synchronous token hook");
        assertEquals(async ? 1 : 0, level.asyncCalls, "existing asynchronous token hook");
        assertArrayEquals(PAYLOAD, level.seenPayload, "the custom hook still receives the full column");
        fixture.assertToken(level.seenToken);
        fixture.assertNoProviderFailure();

        if (async) {
            Object prepared = fixture.onlyPreparedCallback();
            NetworkChunkSerializerCallbackData data = preparedData(prepared);
            assertArrayEquals(PAYLOAD, data.getPayload());
            assertSame(level.seenToken, field(prepared, "token"));
            assertNull(data.getSubChunkBlobs(), "legacy custom levels keep the full-payload route");
            assertNull(data.getBiomes());
            assertNull(data.getBlockEntities());
        } else {
            assertTrue(fixture.prepared.isEmpty());
        }
    }

    @ParameterizedTest
    @CsvSource({"ANVIL,false", "ANVIL,true", "LEVELDB,false", "LEVELDB,true"})
    void exactNativeLevelKeepsStructuredBundleAndOriginalSourceToken(
            ProviderKind kind, boolean async) throws Exception {
        Level level = mock(Level.class, CALLS_REAL_METHODS);
        assertEquals(Level.class, level.getClass(), "control must exercise the exact native class");
        Fixture fixture = new Fixture(kind, async, level);

        fixture.serialize();

        ArgumentCaptor<ChunkRequestToken> token = ArgumentCaptor.forClass(ChunkRequestToken.class);
        if (async) {
            verify(level).asyncChunkRequestCallback(eq(VERSION), eq(TIMESTAMP), eq(X), eq(Z),
                    eq(SUBCHUNKS), aryEq(PAYLOAD), same(BLOBS), same(BIOMES), same(BLOCK_ENTITIES), token.capture());
            verify(level, never()).asyncChunkRequestCallback(any(GameVersion.class), anyLong(),
                    anyInt(), anyInt(), anyInt(), any(byte[].class), any(ChunkRequestToken.class));
            Object prepared = fixture.onlyPreparedCallback();
            NetworkChunkSerializerCallbackData data = preparedData(prepared);
            assertArrayEquals(PAYLOAD, data.getPayload());
            assertSame(BLOBS, data.getSubChunkBlobs());
            assertSame(BIOMES, data.getBiomes());
            assertSame(BLOCK_ENTITIES, data.getBlockEntities());
            assertSame(token.getValue(), field(prepared, "token"));
        } else {
            verify(level).chunkRequestCallback(eq(VERSION), eq(TIMESTAMP), eq(X), eq(Z),
                    eq(SUBCHUNKS), aryEq(PAYLOAD), same(BLOBS), same(BIOMES), same(BLOCK_ENTITIES), token.capture());
            verify(level, never()).chunkRequestCallback(any(GameVersion.class), anyLong(),
                    anyInt(), anyInt(), anyInt(), any(byte[].class), any(ChunkRequestToken.class));
            assertTrue(fixture.prepared.isEmpty());
        }
        fixture.assertToken(token.getValue());
        fixture.assertNoProviderFailure();
    }

    private static NetworkChunkSerializerCallbackData preparedData(Object prepared) throws Exception {
        return (NetworkChunkSerializerCallbackData) field(prepared, "data");
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void set(Object target, Class<?> owner, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class Fixture {
        final boolean async;
        final Level level;
        final LevelProvider provider;
        final BaseChunk source;
        final BaseChunk copy;
        final Queue<Object> prepared = new ConcurrentLinkedQueue<>();
        final ExecutorService executor = mock(ExecutorService.class);

        Fixture(ProviderKind kind, boolean async, Level level) throws Exception {
            this.async = async;
            this.level = level;
            Server server = mock(Server.class);
            server.asyncChunkSending = async;
            server.cacheChunks = false;
            doReturn(mock(MainLogger.class)).when(server).getLogger();
            set(level, Level.class, "server", server);
            for (String name : new String[]{"chunkSendQueues", "chunkSendTasks", "pendingChunkRequests"}) {
                set(level, Level.class, name, new Object2ObjectOpenHashMap<>());
            }
            set(level, Level.class, "chunkSendTaskStartTick", new Long2LongOpenHashMap());
            set(level, Level.class, "asyncChunkRequestCallbackQueue", prepared);
            set(level, Level.class, "asyncChunkRequestFailedQueue", new ConcurrentLinkedQueue<>());
            doReturn(DIMENSION).when(level).getDimensionData();
            doReturn(false).when(level).antiXrayEnabled();
            doReturn(true).when(level).isClientCacheDeliveryWanted();
            doReturn(executor).when(level).getAsyncChuckExecutor();
            // Execute the submitted async branch inline so the serializer stub stays thread-local.
            // Worker affinity itself belongs to ChunkCacheCompressionThreadTest.
            doAnswer(invocation -> {
                invocation.<Runnable>getArgument(0).run();
                return null;
            }).when(executor).execute(any(Runnable.class));

            if (kind == ProviderKind.ANVIL) {
                provider = mock(Anvil.class, CALLS_REAL_METHODS);
                source = mock(Chunk.class);
                copy = mock(Chunk.class);
                set(provider, BaseLevelProvider.class, "level", level);
            } else {
                provider = mock(LevelDBProvider.class, CALLS_REAL_METHODS);
                source = mock(LevelDBChunk.class);
                copy = mock(LevelDBChunk.class);
                set(provider, LevelDBProvider.class, "level", level);
            }
            doReturn(source).when(provider).getChunk(X, Z, false);
            doReturn(copy).when(source).cloneForChunkSending();
            doReturn(TIMESTAMP).when(source).getChanges();
            doReturn(19L).when(source).getMutationRevision();
            doReturn(19L).when(copy).getMutationRevision();
        }

        void serialize() {
            BinaryStream stream = new BinaryStream();
            stream.put(PAYLOAD);
            NetworkChunkSerializerCallback output = new NetworkChunkSerializerCallback(
                    VERSION, stream, SUBCHUNKS, BLOBS, BIOMES, BLOCK_ENTITIES);
            ObjectSet<GameVersion> protocols = new ObjectOpenHashSet<>();
            protocols.add(VERSION);
            try (MockedStatic<NetworkChunkSerializer> serializer = mockStatic(NetworkChunkSerializer.class)) {
                serializer.when(() -> NetworkChunkSerializer.serialize(same(protocols),
                        same(async ? copy : source), any(), eq(false), same(DIMENSION), eq(true)))
                        .thenAnswer(invocation -> {
                            Consumer<NetworkChunkSerializerCallback> callback = invocation.getArgument(2);
                            callback.accept(output);
                            return null;
                        });
                provider.requestChunkTask(protocols, X, Z);
                serializer.verify(() -> NetworkChunkSerializer.serialize(same(protocols),
                        same(async ? copy : source), any(), eq(false), same(DIMENSION), eq(true)));
            }
            verify(executor, times(async ? 1 : 0)).execute(any(Runnable.class));
        }

        void assertToken(ChunkRequestToken token) {
            assertNotNull(token, "provider must retain the source identity/revision token");
            assertTrue(token.matches(source));
            assertFalse(token.matches(copy), "equal revision does not make the serializer clone the owner");
            doReturn(20L).when(source).getMutationRevision();
            assertFalse(token.matches(source), "the token must not track later source mutations");
        }

        Object onlyPreparedCallback() {
            assertEquals(1, prepared.size(), "delegating through the old hook must enqueue exactly once");
            return prepared.element();
        }

        void assertNoProviderFailure() {
            verify(level, never()).onAsyncChunkRequestFailed(any(GameVersion.class), anyInt(), anyInt());
        }
    }

    static class LegacyHookLevel extends Level {
        int syncCalls;
        int asyncCalls;
        byte[] seenPayload;
        ChunkRequestToken seenToken;

        LegacyHookLevel() {
            super(null, null, null, null);
        }

        @Override
        public void chunkRequestCallback(GameVersion version, long timestamp, int x, int z,
                                         int subChunkCount, byte[] payload, ChunkRequestToken token) {
            if (++syncCalls > 1) throw new AssertionError("recursive synchronous callback bridge");
            seenPayload = payload;
            seenToken = token;
            super.chunkRequestCallback(version, timestamp, x, z, subChunkCount, payload, token);
        }

        @Override
        public void asyncChunkRequestCallback(GameVersion version, long timestamp, int x, int z,
                                              int subChunkCount, byte[] payload, ChunkRequestToken token) {
            if (++asyncCalls > 1) throw new AssertionError("recursive asynchronous callback bridge");
            seenPayload = payload;
            seenToken = token;
            super.asyncChunkRequestCallback(version, timestamp, x, z, subChunkCount, payload, token);
        }
    }

    static class InheritedLegacyHookLevel extends LegacyHookLevel { }

    static class StructuredHookLevel extends LegacyHookLevel {
        int structuredCalls;
        List<byte[]> seenBlobs;
        byte[] seenBiomes;

        @Override public void chunkRequestCallback(GameVersion version, long timestamp, int x, int z,
                int count, byte[] payload, List<byte[]> blobs, byte[] biomes, byte[] entities) {
            if (++structuredCalls > 1) throw new AssertionError("recursive structured callback");
            seenBlobs = blobs;
            seenBiomes = biomes;
            super.chunkRequestCallback(version, timestamp, x, z, count, payload, blobs, biomes, entities);
        }

        @Override public void asyncChunkRequestCallback(GameVersion version, long timestamp, int x, int z,
                int count, byte[] payload, List<byte[]> blobs, byte[] biomes, byte[] entities) {
            if (++structuredCalls > 1) throw new AssertionError("recursive async structured callback");
            seenBlobs = blobs;
            seenBiomes = biomes;
            super.asyncChunkRequestCallback(version, timestamp, x, z, count, payload, blobs, biomes, entities);
        }
    }

    static class InheritedStructuredHookLevel extends StructuredHookLevel { }

    static class NewTokenHookLevel extends StructuredHookLevel {
        int newestCalls;

        @Override public void chunkRequestCallback(GameVersion version, long timestamp, int x, int z,
                int count, byte[] payload, List<byte[]> blobs, byte[] biomes, byte[] entities, ChunkRequestToken token) {
            if (++newestCalls > 1) throw new AssertionError("recursive full token callback");
            seenToken = token;
            super.chunkRequestCallback(version, timestamp, x, z, count, payload, blobs, biomes, entities, token);
        }

        @Override public void asyncChunkRequestCallback(GameVersion version, long timestamp, int x, int z,
                int count, byte[] payload, List<byte[]> blobs, byte[] biomes, byte[] entities, ChunkRequestToken token) {
            if (++newestCalls > 1) throw new AssertionError("recursive async full token callback");
            seenToken = token;
            super.asyncChunkRequestCallback(version, timestamp, x, z, count, payload, blobs, biomes, entities, token);
        }
    }
}
