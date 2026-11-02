package cn.nukkit.level.format.leveldb;

import cn.nukkit.MockServer;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.passive.EntityPig;
import cn.nukkit.level.DimensionEnum;
import cn.nukkit.level.format.leveldb.serializer.ChunkDataLoader;
import cn.nukkit.level.format.leveldb.serializer.EntitySerializer;
import cn.nukkit.level.format.leveldb.structure.ChunkBuilder;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.DoubleTag;
import cn.nukkit.nbt.tag.ListTag;
import net.daporkchop.ldbjni.LevelDB;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.impl.Iq80DBFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.AdditionalAnswers;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Entity loading must not seek the same modern digest twice, including on fallback paths. */
class EntityDigestReadTest {
    private static final int X = 5;
    private static final int Z = -3;
    private static final String LOADABLE_ID = "EntityDigestReadTestPig";

    @TempDir Path directory;
    private Map<String, Class<? extends Entity>> registry;
    private Class<? extends Entity> previousEntity;

    @BeforeAll
    static void initialize() {
        MockServer.init();
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void registerFixture() throws Exception {
        Field field = Entity.class.getDeclaredField("knownEntities");
        field.setAccessible(true);
        this.registry = (Map<String, Class<? extends Entity>>) field.get(null);
        this.previousEntity = this.registry.put(LOADABLE_ID, EntityPig.class);
        assertTrue(Entity.canCreateEntity(LOADABLE_ID), "fixture needs a registered loadable entity");
    }

    @AfterEach
    void restoreFixture() {
        if (this.registry == null) return;
        if (this.previousEntity == null) this.registry.remove(LOADABLE_ID);
        else this.registry.put(LOADABLE_ID, this.previousEntity);
    }

    static Stream<Arguments> databasesAndDimensions() {
        return Stream.of("java", "native")
                .flatMap(backend -> IntStream.rangeClosed(0, 2).mapToObj(dimension -> Arguments.of(backend, dimension)));
    }

    @ParameterizedTest(name = "{0}, dimension {1}")
    @MethodSource("databasesAndDimensions")
    void modernActorsKeepDigestOrderSkipMissingAndSuppressLegacy(String backend, int dimension) throws Exception {
        try (DB db = open(backend)) {
            putActor(db, 11, entity(11));
            putActor(db, 22, entity(22));
            db.put(digestKey(dimension), digest(22, 99, 11)); // 99 has no actor record.
            putLegacy(db, dimension, 33);
            ReadResult result = read(db, dimension);
            assertLoaded(result, 22, 11);
            assertTrue(result.preserved().isEmpty());
            assertEquals(1, result.digestSeeks());
        }
    }

    @ParameterizedTest(name = "{0}, dimension {1}")
    @MethodSource("databasesAndDimensions")
    void absentDigestLoadsLegacyWithOneSeek(String backend, int dimension) throws Exception {
        try (DB db = open(backend)) {
            putLegacy(db, dimension, 33);
            ReadResult result = read(db, dimension);
            assertLoaded(result, 33);
            assertTrue(result.preserved().isEmpty());
            assertEquals(1, result.digestSeeks());
        }
    }

    @ParameterizedTest(name = "{0}, dimension {1}")
    @MethodSource("databasesAndDimensions")
    void emptyDigestRetainsLegacyFallback(String backend, int dimension) throws Exception {
        assertLegacyFallback(backend, dimension, new byte[0]);
    }

    @ParameterizedTest(name = "{0}, dimension {1}")
    @MethodSource("databasesAndDimensions")
    void truncatedDigestRetainsLegacyFallback(String backend, int dimension) throws Exception {
        assertLegacyFallback(backend, dimension, Arrays.copyOf(digest(11), 7));
    }

    private void assertLegacyFallback(String backend, int dimension, byte[] digest) throws Exception {
        try (DB db = open(backend)) {
            putActor(db, 11, entity(11));
            db.put(digestKey(dimension), digest);
            putLegacy(db, dimension, 33);
            ReadResult result = read(db, dimension);
            assertLoaded(result, 33);
            assertTrue(result.preserved().isEmpty());
            assertEquals(1, result.digestSeeks());
        }
    }

    @ParameterizedTest(name = "{0}, dimension {1}")
    @MethodSource("databasesAndDimensions")
    void unknownAndCorruptActorsSurviveWhileLegacySuppliesLoadableEntities(String backend, int dimension) throws Exception {
        assertPreservedActors(backend, dimension, false);
    }

    @ParameterizedTest(name = "{0}, dimension {1}")
    @MethodSource("databasesAndDimensions")
    void loadableModernActorSuppressesLegacyWithoutDroppingOpaqueActors(String backend, int dimension) throws Exception {
        assertPreservedActors(backend, dimension, true);
    }

    private void assertPreservedActors(String backend, int dimension, boolean includeLoadable) throws Exception {
        try (DB db = open(backend)) {
            byte[] unknown = NBTIO.write(new CompoundTag().putString("id", "EntityDigestReadTestUnknown")
                    .putString("opaque", "keep unchanged"), ByteOrder.LITTLE_ENDIAN);
            byte[] corrupt = {(byte) 0xff, 1, 2};
            db.put(actorKey(44), unknown);
            db.put(actorKey(55), corrupt);
            putActor(db, 11, entity(11));
            db.put(digestKey(dimension), includeLoadable ? digest(44, 11, 99, 55) : digest(44, 99, 55));
            putLegacy(db, dimension, 33);
            ReadResult result = read(db, dimension);
            assertLoaded(result, includeLoadable ? 11 : 33);
            assertEquals(2, result.preserved().size());
            assertArrayEquals(digest(44), result.preserved().get(0).getStorageKey());
            assertArrayEquals(unknown, result.preserved().get(0).getRawNbt());
            assertArrayEquals(digest(55), result.preserved().get(1).getStorageKey());
            assertArrayEquals(corrupt, result.preserved().get(1).getRawNbt());
            assertEquals(1, result.digestSeeks());
        }
    }

    private DB open(String backend) throws Exception {
        Options options = new Options().createIfMissing(true);
        if (backend.equals("native")) {
            assumeTrue(LevelDB.PROVIDER.isNative(), "native LevelDB is unavailable on this platform");
            return LevelDB.PROVIDER.open(this.directory.resolve("native").toFile(), options);
        }
        return Iq80DBFactory.factory.open(this.directory.resolve("java").toFile(), options);
    }

    private static ReadResult read(DB db, int dimension) {
        AtomicInteger seeks = new AtomicInteger();
        byte[] digestKey = digestKey(dimension);
        DB counted = mock(DB.class, AdditionalAnswers.delegatesTo(db));
        doAnswer(call -> {
            DBIterator real = db.iterator();
            DBIterator iterator = mock(DBIterator.class, AdditionalAnswers.delegatesTo(real));
            doAnswer(seek -> {
                byte[] key = seek.getArgument(0);
                if (Arrays.equals(digestKey, key)) seeks.incrementAndGet();
                real.seek(key);
                return null;
            }).when(iterator).seek(any(byte[].class));
            return iterator;
        }).when(counted).iterator();

        AtomicReference<List<CompoundTag>> entities = new AtomicReference<>(List.of());
        AtomicReference<List<LevelDBChunk.PreservedEntityActor>> preserved = new AtomicReference<>(List.of());
        LevelDBChunk chunk = mock(LevelDBChunk.class);
        doAnswer(call -> { entities.set(call.getArgument(0)); return null; }).when(chunk).setNbtEntities(any());
        doAnswer(call -> { preserved.set(call.getArgument(0)); return null; }).when(chunk).setPreservedEntityActors(any());
        ChunkBuilder builder = mock(ChunkBuilder.class);
        when(builder.getChunkX()).thenReturn(X);
        when(builder.getChunkZ()).thenReturn(Z);
        when(builder.getDimensionData()).thenReturn(DimensionEnum.getDataFromId(dimension));
        doAnswer(call -> {
            call.getArgument(0, ChunkDataLoader.class).initChunk(chunk, null);
            return builder;
        }).when(builder).dataLoader(any(ChunkDataLoader.class));
        try (ChunkColumnReader reader = ChunkColumnReader.column(counted, X, Z, dimension)) {
            EntitySerializer.loadEntities(reader, builder);
        }
        return new ReadResult(entities.get(), preserved.get(), seeks.get());
    }

    private static void assertLoaded(ReadResult result, long... ids) {
        assertArrayEquals(ids, result.entities().stream().mapToLong(tag -> tag.getLong("UniqueID")).toArray());
        for (CompoundTag tag : result.entities()) {
            assertEquals(LOADABLE_ID, tag.getString("id"));
            assertEquals(3, tag.getList("Pos", DoubleTag.class).size());
            assertTrue(tag.contains("Motion"), "the real load adapter must normalize each accepted actor");
        }
    }

    private static CompoundTag entity(long id) {
        return new CompoundTag().putString("id", LOADABLE_ID).putLong("UniqueID", id)
                .putList(new ListTag<DoubleTag>("Pos").add(new DoubleTag("", 1))
                        .add(new DoubleTag("", 64)).add(new DoubleTag("", 2)));
    }

    private static void putActor(DB db, long id, CompoundTag tag) throws Exception {
        db.put(actorKey(id), NBTIO.write(tag, ByteOrder.LITTLE_ENDIAN));
    }

    private static void putLegacy(DB db, int dimension, long id) throws Exception {
        db.put(LevelDBKey.ENTITIES.getKey(X, Z, dimension), NBTIO.write(entity(id), ByteOrder.LITTLE_ENDIAN));
    }

    private static byte[] digestKey(int dimension) {
        return LevelDBKey.getKey(LevelDBKey.DIGP_PREFIX, X, Z, dimension);
    }

    private static byte[] actorKey(long id) {
        return LevelDBKey.getKey(LevelDBKey.ACTOR_PREFIX, digest(id));
    }

    private static byte[] digest(long... ids) {
        ByteBuffer buffer = ByteBuffer.allocate(ids.length * Long.BYTES).order(ByteOrder.BIG_ENDIAN);
        for (long id : ids) buffer.putLong(id);
        return buffer.array();
    }

    private record ReadResult(List<CompoundTag> entities,
                              List<LevelDBChunk.PreservedEntityActor> preserved, int digestSeeks) { }
}
