package cn.nukkit.level;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseChunk;
import cn.nukkit.level.format.generic.BaseLevelProvider;
import cn.nukkit.level.format.generic.serializer.ChunkRequestToken;
import cn.nukkit.level.format.generic.serializer.NetworkChunkSerializer;
import cn.nukkit.level.format.anvil.Anvil;
import cn.nukkit.level.format.anvil.Chunk;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.utils.BinaryStream;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChunkRequestProviderTokenTest {
    @BeforeAll static void init() { MockServer.init(); }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void bothProvidersCarryTheLiveOwnerThroughSyncAndAsyncSerialization(boolean anvil, boolean async) throws Exception {
        Level level = mock(Level.class);
        Server server = mock(Server.class);
        server.asyncChunkSending = async;
        when(level.getServer()).thenReturn(server);
        when(level.getDimensionData()).thenReturn(new DimensionData(0, 0, 15));
        ExecutorService executor = mock(ExecutorService.class);
        when(level.getAsyncChuckExecutor()).thenReturn(executor);
        doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; })
                .when(executor).execute(any(Runnable.class));
        LevelProvider provider;
        BaseChunk source;
        BaseChunk copy;
        if (anvil) {
            provider = mock(Anvil.class, CALLS_REAL_METHODS);
            Chunk chunk = mock(Chunk.class);
            Chunk clone = mock(Chunk.class);
            when(chunk.cloneForChunkSending()).thenReturn(clone);
            source = chunk;
            copy = clone;
        } else {
            provider = mock(LevelDBProvider.class, CALLS_REAL_METHODS);
            LevelDBChunk chunk = mock(LevelDBChunk.class);
            LevelDBChunk clone = mock(LevelDBChunk.class);
            when(chunk.cloneForChunkSending()).thenReturn(clone);
            source = chunk;
            copy = clone;
        }
        Field field = (anvil ? BaseLevelProvider.class : LevelDBProvider.class).getDeclaredField("level");
        field.setAccessible(true);
        field.set(provider, level);
        doReturn(source).when(provider).getChunk(3, 5, false);
        when(source.getChanges()).thenReturn(7L);
        when(source.getMutationRevision()).thenReturn(11L);
        ObjectSet<GameVersion> versions = new ObjectOpenHashSet<>();
        versions.add(GameVersion.V1_20_0);
        byte[] payload = {1, 2, 3};
        try (var serializer = mockStatic(NetworkChunkSerializer.class)) {
            serializer.when(() -> NetworkChunkSerializer.serialize(eq(versions), any(BaseChunk.class),
                    any(), eq(false), any(DimensionData.class))).thenAnswer(call -> {
                assertSame(async ? copy : source, call.getArgument(1));
                Consumer<NetworkChunkSerializer.NetworkChunkSerializerCallback> callback = call.getArgument(2);
                callback.accept(new NetworkChunkSerializer.NetworkChunkSerializerCallback(
                        GameVersion.V1_20_0, new BinaryStream(payload), 1));
                return null;
            });
            provider.requestChunkTask(versions, 3, 5);
        }
        ArgumentCaptor<ChunkRequestToken> token = ArgumentCaptor.forClass(ChunkRequestToken.class);
        if (async) verify(level).asyncChunkRequestCallback(eq(GameVersion.V1_20_0), eq(7L),
                eq(3), eq(5), eq(1), eq(payload), token.capture());
        else verify(level).chunkRequestCallback(eq(GameVersion.V1_20_0), eq(7L),
                eq(3), eq(5), eq(1), eq(payload), token.capture());
        assertTrue(token.getValue().matches(source));
        assertFalse(token.getValue().matches(copy));
        when(source.getMutationRevision()).thenReturn(12L);
        when(source.getChanges()).thenReturn(0L);
        assertFalse(token.getValue().matches(source), "a durable save cannot validate an older serialization");
    }
}
