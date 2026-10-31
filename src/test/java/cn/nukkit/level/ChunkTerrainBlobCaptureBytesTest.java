package cn.nukkit.level;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.blockentity.BlockEntitySpawnable;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.anvil.Anvil;
import cn.nukkit.level.format.anvil.Chunk;
import cn.nukkit.level.format.generic.BaseChunk;
import cn.nukkit.level.format.generic.serializer.NetworkChunkSerializer;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.process.processor.common.ClientCacheBlobStatusProcessor;
import cn.nukkit.network.protocol.ClientCacheBlobStatusPacket;
import cn.nukkit.network.protocol.ClientCacheMissResponsePacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.LevelChunkPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.ThreadCache;
import cn.nukkit.utils.XXHash64;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class ChunkTerrainBlobCaptureBytesTest {
    private static final int X = 3;
    private static final int Z = 5;
    private static final DimensionData DIMENSION = new DimensionData(Level.DIMENSION_OVERWORLD, -64, 319);

    enum ProviderKind { ANVIL, LEVELDB }

    @BeforeAll
    static void initializeServer() { MockServer.init(); }

    @AfterAll
    static void cleanupServer() { MockServer.reset(); }

    static Stream<Arguments> allVersions() {
        List<GameVersion> versions = Stream.concat(Stream.of(GameVersion.V1_18_0, GameVersion.V1_18_30),
                Arrays.stream(GameVersion.values()).filter(version -> !version.isNetEase()
                        && version.getProtocol() >= GameVersion.V1_20_0.getProtocol()
                        && version.getProtocol() <= GameVersion.V1_26_50.getProtocol())).toList();
        return combinations(versions);
    }

    static Stream<Arguments> representativeVersions() {
        return combinations(List.of(GameVersion.V1_18_0, GameVersion.V1_18_30, GameVersion.V1_26_50));
    }

    static Stream<Arguments> wireVersions() {
        return combinations(List.of(GameVersion.V1_18_0, GameVersion.V1_26_50));
    }

    private static Stream<Arguments> combinations(List<GameVersion> versions) {
        return Arrays.stream(ProviderKind.values())
                .flatMap(kind -> versions.stream().map(version -> Arguments.of(kind, version)));
    }

    @ParameterizedTest
    @MethodSource("allVersions")
    void everyCapturedBlobEqualsItsIndependentSectionEncoding(ProviderKind kind, GameVersion version) throws Exception {
        Fixture fixture = new Fixture(kind);
        List<byte[]> expected = fixture.independentBlobs(version);
        Serialized actual = serialize(fixture.source, version, true, false);

        assertEquals(expected.size(), actual.count);
        assertNotNull(actual.blobs);
        assertEquals(expected.size(), actual.blobs.size());
        int syntheticCount = kind == ProviderKind.ANVIL ? 4 : 0;
        for (int i = 0; i < expected.size(); i++) {
            assertArrayEquals(expected.get(i), actual.blobs.get(i),
                    (i < syntheticCount ? "synthetic negative section " : "real section ") + i);
        }
        byte[] expectedPrefix = concatenate(expected);
        assertTrue(actual.payload.length > expectedPrefix.length);
        assertArrayEquals(expectedPrefix, Arrays.copyOf(actual.payload, expectedPrefix.length));
        assertSamePassColumn(actual);
    }

    @ParameterizedTest
    @MethodSource("representativeVersions")
    void capturePreservesFullColumnAndOwnsBytesAcrossTheNextSerialization(
            ProviderKind kind, GameVersion version) throws Exception {
        Fixture fixture = new Fixture(kind);
        Serialized captured = serialize(fixture.source, version, true, false);
        Serialized saved = captured.copy();
        Serialized plain = serialize(fixture.source, version, false, false);
        assertNull(plain.blobs);
        assertEquals(captured.count, plain.count);
        assertArrayEquals(captured.payload, plain.payload);
        assertArrayEquals(captured.biomes, plain.biomes);
        assertArrayEquals(captured.entities, plain.entities);
        assertTrue(captured.biomes.length > 0);
        assertTrue(captured.entities.length > 0, "fixture must include real serialized block-entity NBT");

        fixture.source.setBlock(1, 1, 2, Block.DIRT);
        Serialized next = serialize(fixture.source, version, true, false);
        assertFalse(Arrays.equals(saved.payload, next.payload), "the second serialization must contain different terrain");
        byte[] overwrite = new byte[Math.max(saved.payload.length, next.payload.length) + 512];
        Arrays.fill(overwrite, (byte) 0x5a);
        ThreadCache.binaryStream.get().reset().put(overwrite);

        assertArrayEquals(saved.payload, captured.payload);
        assertArrayEquals(saved.biomes, captured.biomes);
        assertArrayEquals(saved.entities, captured.entities);
        assertEquals(saved.blobs.size(), captured.blobs.size());
        for (int i = 0; i < saved.blobs.size(); i++) {
            assertArrayEquals(saved.blobs.get(i), captured.blobs.get(i), "published blob survives scratch reuse: " + i);
        }
    }

    @ParameterizedTest
    @MethodSource("wireVersions")
    void antiXrayCaptureUsesTheBytesFromTheSameSerializationPass(
            ProviderKind kind, GameVersion version) throws Exception {
        Fixture fixture = new Fixture(kind);
        // Do not independently encode anti-Xray twice: obfuscation may choose different values.
        assertSamePassColumn(serialize(fixture.source, version, true, true));
    }

    @ParameterizedTest
    @MethodSource("wireVersions")
    void nativeCacheMissResponseDeliversIndependentSectionBytes(
            ProviderKind kind, GameVersion version) throws Exception {
        Fixture fixture = new Fixture(kind);
        List<byte[]> expectedBlobs = fixture.independentBlobs(version);
        Serialized captured = serialize(fixture.source, version, true, false);
        LevelChunkPacket column = fixture.level.buildBlobLevelChunkPacket(version, X, Z, captured.count,
                captured.blobs, captured.biomes, captured.entities);

        PlayerBlobCacheState state = new PlayerBlobCacheState(fixture.store, 64);
        assertTrue(state.tryOpenTransaction(column.blobIds, 1));
        PlayerSubChunkRequestState flow = new PlayerSubChunkRequestState();
        Player player = mock(Player.class);
        doReturn(fixture.server).when(player).getServer();
        set(player, Player.class, "blobCacheState", state);
        set(player, Player.class, "subChunkRequestState", flow);
        List<byte[]> wirePackets = new ArrayList<>();
        doAnswer(call -> {
            ClientCacheMissResponsePacket response = assertInstanceOf(ClientCacheMissResponsePacket.class,
                    call.getArgument(0, DataPacket.class));
            response.gameVersion = version;
            response.protocol = version.getProtocol();
            response.tryEncode();
            wirePackets.add(response.getBuffer());
            return true;
        }).when(player).dataPacket(any(DataPacket.class));
        ClientCacheBlobStatusPacket status = new ClientCacheBlobStatusPacket();
        status.missHashes = Arrays.stream(column.blobIds).distinct().toArray();

        ClientCacheBlobStatusProcessor.INSTANCE.handle(new PlayerHandle(player), status);

        assertFalse(wirePackets.isEmpty(), "exercise the processor and native response encoder before inspecting bytes");
        Map<Long, byte[]> delivered = new LinkedHashMap<>();
        for (byte[] wire : wirePackets) {
            BinaryStream input = new BinaryStream(wire);
            assertEquals(ProtocolInfo.toNewProtocolID(ProtocolInfo.CLIENT_CACHE_MISS_RESPONSE_PACKET), input.getUnsignedVarInt());
            int entries = Math.toIntExact(input.getUnsignedVarInt());
            for (int i = 0; i < entries; i++) {
                long hash = input.getLLong();
                assertNull(delivered.put(hash, input.getByteArray()), "each requested distinct hash is answered once");
            }
            assertEquals(wire.length, input.getOffset(), "consume the entire encoded response");
        }
        assertEquals(status.missHashes.length, delivered.size());
        assertEquals(expectedBlobs.size() + 1, column.blobIds.length);
        for (int i = 0; i < expectedBlobs.size(); i++) {
            byte[] expected = expectedBlobs.get(i);
            long advertisedHash = column.blobIds[i];
            assertArrayEquals(expected, delivered.get(advertisedHash), "native miss response section " + i);
            assertEquals(XXHash64.hash(expected), advertisedHash);
            assertArrayEquals(expected, fixture.store.get(advertisedHash));
        }
        long biomesHash = column.blobIds[expectedBlobs.size()];
        assertEquals(XXHash64.hash(captured.biomes), biomesHash);
        assertArrayEquals(captured.biomes, delivered.get(biomesHash));
        assertEquals(0, column.data[0]);
        assertArrayEquals(captured.entities, Arrays.copyOfRange(column.data, 1, column.data.length));
        assertEquals(0, state.getPendingSize());
        assertEquals(0, state.getOpenTransactionCount());
        assertEquals(0, fixture.store.pinnedByteSize());
        assertEquals(0, flow.deferredMissHashCount());
    }

    private static Serialized serialize(BaseChunk chunk, GameVersion version, boolean capture, boolean antiXray) {
        ObjectSet<GameVersion> versions = new ObjectOpenHashSet<>();
        versions.add(version);
        List<Serialized> output = new ArrayList<>();
        NetworkChunkSerializer.serialize(versions, chunk, value -> output.add(new Serialized(value.getSubchunks(),
                value.getStream().getBuffer(), value.getSubChunkBlobs(), value.getBiomes(), value.getBlockEntities())),
                antiXray, DIMENSION, capture);
        assertEquals(1, output.size());
        return output.get(0);
    }

    private static void assertSamePassColumn(Serialized captured) {
        assertNotNull(captured.blobs);
        assertEquals(captured.count, captured.blobs.size());
        BinaryStream expected = new BinaryStream();
        for (byte[] blob : captured.blobs) expected.put(blob);
        expected.put(captured.biomes);
        // Preserve both existing border writes (version-specific body, then common suffix).
        expected.putByte((byte) 0);
        expected.putByte((byte) 0);
        expected.put(captured.entities);
        assertArrayEquals(captured.payload, expected.getBuffer(), "captured blobs must cover the full terrain prefix exactly once");
    }

    private static byte[] concatenate(List<byte[]> values) {
        BinaryStream result = new BinaryStream();
        for (byte[] value : values) result.put(value);
        return result.getBuffer();
    }

    private record Serialized(int count, byte[] payload, List<byte[]> blobs, byte[] biomes, byte[] entities) {
        Serialized copy() {
            return new Serialized(count, payload.clone(), blobs.stream().map(byte[]::clone).toList(),
                    biomes.clone(), entities.clone());
        }
    }

    private static final class Fixture {
        final ProviderKind kind;
        final ClientCacheBlobStore store = new ClientCacheBlobStore(1 << 20);
        final Server server = mock(Server.class);
        final Level level = mock(Level.class, CALLS_REAL_METHODS);
        final BaseChunk source;

        Fixture(ProviderKind kind) throws Exception {
            this.kind = kind;
            doReturn(store).when(server).getClientCacheBlobStore();
            doReturn(1).when(server).getTick();
            set(level, Level.class, "server", server);
            doReturn(DIMENSION).when(level).getDimensionData();
            doReturn(Level.DIMENSION_OVERWORLD).when(level).getDimension();
            LevelProvider provider = kind == ProviderKind.ANVIL ? mock(Anvil.class) : mock(LevelDBProvider.class);
            doReturn(level).when(provider).getLevel();
            doReturn(kind == ProviderKind.ANVIL ? 0 : -64).when(provider).getMinBlockY();
            source = kind == ProviderKind.ANVIL ? Chunk.getEmptyChunk(X, Z, provider)
                    : LevelDBChunk.getEmptyChunk(X, Z, provider);
            assertNotNull(source);
            source.setBlock(1, 1, 2, Block.DIAMOND_ORE);
            source.setBlock(3, 17, 4, Block.CHEST);
            source.setBiomeId(1, 2, 2);
            assertFalse(source.getSection(0).isEmpty());
            assertFalse(source.getSection(1).isEmpty());
            if (kind == ProviderKind.LEVELDB) {
                source.setBlock(2, -49, 3, Block.GOLD_ORE);
                source.setBlock(4, -17, 5, Block.DIRT);
                assertFalse(source.getSection(-4).isEmpty(), "include real negative-height terrain");
                assertFalse(source.getSection(-2).isEmpty());
            }
            BlockEntitySpawnable entity = mock(BlockEntitySpawnable.class);
            doReturn(1L).when(entity).getId();
            doReturn((X << 4) + 3).when(entity).getFloorX();
            doReturn(17).when(entity).getFloorY();
            doReturn((Z << 4) + 4).when(entity).getFloorZ();
            doReturn(new CompoundTag().putString("id", "Chest").putInt("x", (X << 4) + 3)
                    .putInt("y", 17).putInt("z", (Z << 4) + 4)).when(entity).getSpawnCompound(anyInt());
            source.addBlockEntity(entity);
        }

        List<byte[]> independentBlobs(GameVersion version) {
            List<byte[]> result = new ArrayList<>();
            if (kind == ProviderKind.ANVIL) {
                for (int sectionY = -4; sectionY < 0; sectionY++) {
                    result.add(version.getProtocol() >= ProtocolInfo.v1_19_80
                            ? new byte[]{9, 0, (byte) sectionY} : new byte[]{8, 0});
                }
            }
            // The fixture's highest occupied section is y=1, with real LevelDB terrain below zero.
            int realSections = source.getSectionOffset() + 2;
            for (int i = 0; i < realSections; i++) {
                BinaryStream independent = new BinaryStream();
                source.getSections()[i].writeTo(version, independent, false);
                byte[] bytes = independent.getBuffer();
                assertTrue(bytes.length > 0, "independent section encoder must emit at least its header");
                result.add(bytes);
            }
            return result;
        }
    }

    private static void set(Object target, Class<?> owner, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
