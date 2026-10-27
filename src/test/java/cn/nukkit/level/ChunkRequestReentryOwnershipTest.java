package cn.nukkit.level;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.level.format.anvil.Chunk;
import cn.nukkit.level.format.generic.BaseChunk;
import cn.nukkit.level.format.generic.serializer.NetworkChunkSerializer;
import cn.nukkit.level.format.generic.serializer.NetworkChunkSerializer.NetworkChunkSerializerCallback;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.network.protocol.LevelChunkPacket;
import cn.nukkit.utils.BinaryStream;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static cn.nukkit.level.ChunkInitialSerializationOwnershipTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Lookups must not transfer an outer dispatch to a nested accepted replacement. */
@Isolated("MockServer and static serializer fixtures")
@Timeout(20)
class ChunkRequestReentryOwnershipTest {
    enum Boundary { LEVEL_LOOKUP, PROVIDER_LOOKUP }
    @BeforeAll static void initializeServer() { MockServer.init(); }

    @ParameterizedTest
    @CsvSource({"LEVELDB,false,LEVEL_LOOKUP", "LEVELDB,true,LEVEL_LOOKUP",
            "ANVIL,false,LEVEL_LOOKUP", "ANVIL,true,LEVEL_LOOKUP",
            "LEVELDB,false,PROVIDER_LOOKUP", "LEVELDB,true,PROVIDER_LOOKUP",
            "ANVIL,false,PROVIDER_LOOKUP", "ANVIL,true,PROVIDER_LOOKUP"})
    void reentrantLookupCannotCompleteOrSubmitTheReplacementTwice(
            ProviderKind kind, boolean async, Boundary boundary) throws Exception {
        Fixture f = new Fixture(kind, Mode.FULL_UNCACHED);
        BaseChunk fresh = kind == ProviderKind.LEVELDB ? mock(LevelDBChunk.class) : mock(Chunk.class);
        doReturn(f.newCopy).when(fresh).cloneForChunkSending();
        when(fresh.getChanges()).thenReturn(7L);
        when(fresh.getMutationRevision()).thenReturn(7L);
        AtomicReference<BaseChunk> current = new AtomicReference<>(f.source);
        doAnswer(call -> current.get()).when(f.level).getChunk(X, Z);
        doAnswer(call -> current.get()).when(f.provider).getChunk(X, Z, false);
        AtomicBoolean armed = new AtomicBoolean(), reentered = new AtomicBoolean();
        AtomicReference<Int2ObjectMap<Player>> replacement = new AtomicReference<>();
        AtomicInteger freshSerializations = new AtomicInteger();
        org.mockito.stubbing.Answer<BaseChunk> lookup = call -> {
            BaseChunk result = current.get();
            if (armed.getAndSet(false)) {
                reentered.set(true);
                current.set(fresh);
                f.requestAt(202, f.player);
                replacement.set(f.owners().get(INDEX));
            }
            return result;
        };
        if (boundary == Boundary.LEVEL_LOOKUP) doAnswer(lookup).when(f.level).getChunk(X, Z);
        else doAnswer(lookup).when(f.provider).getChunk(X, Z, false);
        doAnswer(call -> current.get()).when(f.level).getChunkIfLoaded(X, Z);

        try (var serializer = mockStatic(NetworkChunkSerializer.class)) {
            serializer.when(() -> NetworkChunkSerializer.serialize(any(ObjectSet.class), any(BaseChunk.class),
                    any(), eq(false), same(DimensionData.LEGACY_DIMENSION), eq(true))).thenAnswer(call -> {
                BaseChunk input = call.getArgument(1);
                boolean isFresh = input == fresh || input == f.newCopy;
                ObjectSet<GameVersion> protocols = call.getArgument(0);
                if (isFresh && protocols.contains(VERSION)) freshSerializations.incrementAndGet();
                Consumer<NetworkChunkSerializerCallback> callback = call.getArgument(2);
                for (GameVersion version : protocols) {
                    BinaryStream stream = new BinaryStream();
                    stream.put(isFresh ? NEW_PAYLOAD : OLD_PAYLOAD);
                    callback.accept(new NetworkChunkSerializerCallback(version, stream, 2,
                            List.of(new byte[]{51}), new byte[]{41, 42}, new byte[0]));
                }
                return null;
            });
            if (boundary == Boundary.LEVEL_LOOKUP) {
                Player sibling = f.player(2);
                when(sibling.getGameVersion()).thenReturn(GameVersion.V1_20_0);
                f.requestAt(1, sibling);
                assertEquals(1, f.work.size());
                // Existing sibling work owns the coordinate-wide start timestamp.
                f.work.clear();
            }
            f.server.asyncChunkSending = async;
            armed.set(true);
            f.requestAt(1, f.player);
            assertTrue(reentered.get(), "lookup must accept a real retry at tick 202");
            if (async) {
                assertNotNull(replacement.get());
                if (boundary == Boundary.PROVIDER_LOOKUP && f.work.size() > 1) {
                    Runnable outer = f.work.stream().reduce((first, last) -> last).orElseThrow();
                    f.work.remove(outer);
                    outer.run();
                    f.level.doTick(202);
                    assertTrue(f.sent.isEmpty(), "late outer result cannot deliver to nested retry");
                    assertTrue(f.tasks().contains(INDEX));
                    assertSame(replacement.get(), f.owners().get(INDEX));
                }
                f.drainWork(203);
            }
            assertEquals(1, freshSerializations.get(), "nested replacement snapshot serialized exactly once");
            assertEquals(1, f.sent.size());
            assertArrayEquals(NEW_PAYLOAD, assertInstanceOf(LevelChunkPacket.class, f.sent.get(0)).data);
            assertFalse(f.tasks().contains(INDEX));
            assertFalse(f.owners().containsKey(INDEX));
            if (boundary == Boundary.LEVEL_LOOKUP)
                verify(f.provider, times(2)).requestChunkTask(any(ObjectSet.class), eq(X), eq(Z));
        }
    }
}
