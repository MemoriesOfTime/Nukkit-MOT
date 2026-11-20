package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.level.DimensionData;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.ChunkSection;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.leveldb.LevelDBKey;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import cn.nukkit.level.format.leveldb.serializer.ChunkSerializerV3;
import cn.nukkit.level.format.leveldb.serializer.Data3dSerializer;
import cn.nukkit.level.util.BitArray;
import cn.nukkit.level.util.BitArrayVersion;
import cn.nukkit.level.util.PalettedBlockStorage;
import cn.nukkit.utils.BlockUpdateEntry;
import io.netty.buffer.AdaptiveByteBufAllocator;
import io.netty.buffer.ByteBufAllocator;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.nbt.NbtUtils;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.WriteBatch;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.StampedLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Native selected-core methods; mocked provider/DB boundaries only, no Server or registry startup. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Isolated("Mutates the global allocator pool and Server singleton")
@Timeout(value = 10, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SAME_THREAD)
public class ByteArrayStagingTest {
    private static final DimensionData DIM = new DimensionData(0, -64, 319);
    private static final int X = -7, Z = 11;
    private static Object previousServer;

    @BeforeAll static void installMinimalServerForProviderClassInit() throws Exception {
        Field singleton = field(cn.nukkit.Server.class, "instance");
        previousServer = singleton.get(null);
        if (previousServer == null) singleton.set(null, mock(cn.nukkit.Server.class));
    }
    @AfterAll static void restoreServerSingleton() throws Exception {
        field(cn.nukkit.Server.class, "instance").set(null, previousServer);
    }

    @Test @Order(1)
    void data3dUnsignedHeightsAndCopyLastRoundTrip() throws Exception {
        LevelDBProvider provider = provider();
        int[] heights = heights();
        PalettedBlockStorage[] biomes = biomes();
        LevelDBChunk chunk = chunk(provider, new ChunkSection[24], heights, biomes);
        CapturingBatch batch = new CapturingBatch();
        Data3dSerializer.serialize(batch, chunk);
        byte[] data = batch.value(LevelDBKey.DATA_3D.getKey(X, Z, 0));
        corpus("data3d", data);
        assertEquals(0, unsignedShort(data, 0));
        assertEquals(319, unsignedShort(data, 2));
        assertEquals(32767, unsignedShort(data, 4));
        assertEquals(65535, unsignedShort(data, 6));
        assertEquals(1, data[512] & 255); // V0 runtime palette, then one int ID.
        assertEquals(255, data[517] & 255); // equal but distinct storage.
        assertEquals(1, data[518] & 255); // changed palette breaks COPY_LAST.
        ChunkBuilder builder = decode(provider, data);
        assertArrayEquals(heights, builder.heightMap);
        assertEquals(24, builder.biomes3d.length);
        for (int i = 0; i < 24; i++) {
            assertTrue(biomes[i].contentEquals(builder.biomes3d[i]), "biome section " + i);
            if (i > 0) assertNotSame(builder.biomes3d[i - 1], builder.biomes3d[i]);
        }
        CapturingBatch roundTrip = new CapturingBatch();
        Data3dSerializer.serialize(roundTrip, chunk(provider, new ChunkSection[24], builder.heightMap, builder.biomes3d));
        assertArrayEquals(data, roundTrip.value(LevelDBKey.DATA_3D.getKey(X, Z, 0)));
    }

    @Test @Order(2)
    void data3dMissingAndTruncatedBiomesPreserveExistingContract() throws Exception {
        LevelDBProvider provider = provider();
        for (byte[] absent : new byte[][] { null, new byte[0] }) {
            ChunkBuilder builder = decode(provider, absent);
            assertNull(builder.heightMap);
            assertNull(builder.biomes3d);
        }
        byte[] heightsOnly = new byte[512];
        heightsOnly[2] = 63; heightsOnly[3] = 1;
        ChunkBuilder missing = decode(provider, heightsOnly);
        assertEquals(319, missing.heightMap[1]);
        assertEquals(24, missing.biomes3d.length);
        for (int i = 0; i < 24; i++) {
            assertEquals(0, missing.biomes3d[i].getBlock(0, 0, 0));
            if (i > 0) assertNotSame(missing.biomes3d[i - 1], missing.biomes3d[i]);
        }
        corpus("data3d-heights-only", heightsOnly);
        byte[] invalidCopy = Arrays.copyOf(heightsOnly, 513); invalidCopy[512] = (byte) 255;
        assertThrows(IllegalStateException.class, () -> decode(provider, invalidCopy));
        assertThrows(IndexOutOfBoundsException.class, () -> decode(provider, new byte[511]));
        byte[] truncatedPalette = Arrays.copyOf(heightsOnly, 515); truncatedPalette[512] = 1;
        assertThrows(IndexOutOfBoundsException.class, () -> decode(provider, truncatedPalette));
        byte[] completeFirst = Arrays.copyOf(heightsOnly, 518);
        completeFirst[512] = 1; completeFirst[513] = 42; completeFirst[517] = (byte) 255;
        ChunkBuilder partial = decode(provider, completeFirst);
        for (PalettedBlockStorage storage : partial.biomes3d) assertEquals(42, storage.getBlock(0, 0, 0));
        corpus("data3d-partial-biomes", completeFirst);
    }

    @Test @Order(3)
    void subchunkTwoLayersEdgesAndSaveTokenAckFence() throws Exception {
        LevelDBProvider provider = provider();
        ChunkSection[] sections = new ChunkSection[24];
        LevelDBChunkSection lower = section(-4, 17);
        LevelDBChunkSection upper = section(19, 29);
        sections[0] = lower; sections[23] = upper;
        LevelDBChunk chunk = chunk(provider, sections, heights(), biomes());
        lower.setDirty(); upper.setDirty();
        CapturingBatch batch = new CapturingBatch();
        List<LevelDBChunkSection.SaveToken> tokens = new ArrayList<>();
        ChunkSerializerV3.INSTANCE.serializer(batch, chunk, tokens::add);
        assertEquals(2, tokens.size());
        assertEquals(22, batch.deletes.size());
        assertTrue(lower.isDirty()); assertTrue(upper.isDirty()); // capture is not a DB ACK.
        byte[] lowerBytes = batch.value(LevelDBKey.SUB_CHUNK_PREFIX.getKey(X, Z, -4, 0));
        byte[] upperBytes = batch.value(LevelDBKey.SUB_CHUNK_PREFIX.getKey(X, Z, 19, 0));
        assertEquals(9, lowerBytes[0] & 255); assertEquals(2, lowerBytes[1] & 255);
        assertEquals(-4, lowerBytes[2]); assertEquals(19, upperBytes[2]);
        assertEquals(0, lower.getStorages()[0].getBlockState(0).getLegacyData());
        assertEquals(1, lower.getStorages()[0].getBlockState(4095).getLegacyData());
        corpus("subchunk-y-minus4", lowerBytes); corpus("subchunk-y19", upperBytes);
        corpus("subchunk-deletes", batch.deleteBytes());
        lower.setDirty(); // revision changes after the bytes have been captured.
        tokens.forEach(LevelDBChunkSection.SaveToken::acknowledge); // stand-in successful DB.write boundary.
        assertTrue(lower.isDirty(), "stale token must not clear the newer mutation");
        assertFalse(upper.isDirty(), "unchanged captured section can be acknowledged");
        List<LevelDBChunkSection.SaveToken> next = new ArrayList<>();
        ChunkSerializerV3.INSTANCE.serializer(new CapturingBatch(), chunk, next::add);
        assertEquals(1, next.size()); next.get(0).acknowledge(); assertFalse(lower.isDirty());
    }

    @Test @Order(4)
    void nativePendingNbtStagingNonemptyAndEmptyDelete() throws Exception {
        NbtMap ticks = ticks();
        CapturingBatch batch = pending(ticks, true);
        byte[] bytes = batch.value(LevelDBKey.PENDING_TICKS.getKey(X, Z, 0));
        corpus("pending-ticks", bytes);
        try (var reader = NbtUtils.createReaderLE(new ByteArrayInputStream(bytes))) {
            assertEquals(ticks, reader.readTag());
        }
        CapturingBatch empty = pending(ticks, false);
        assertNull(empty.valueOrNull(LevelDBKey.PENDING_TICKS.getKey(X, Z, 0)));
        assertTrue(empty.deleted(LevelDBKey.PENDING_TICKS.getKey(X, Z, 0)));
        corpus("pending-empty-batch", empty.allBytes());
    }

    @Test @Order(5)
    void subchunkDoesNotWaitForSharedAdaptiveMagazine() throws Exception {
        LevelDBProvider provider = provider();
        ChunkSection[] sections = new ChunkSection[24]; sections[0] = section(-4, 17);
        LevelDBChunk chunk = chunk(provider, sections, heights(), biomes());
        Runnable operation = () -> ChunkSerializerV3.INSTANCE.serializer(new CapturingBatch(), chunk);
        operation.run(); sections[0].setDirty();
        withoutGlobalAdaptiveWait("subchunk", operation);
    }

    @Test @Order(6)
    void data3dWriteDoesNotWaitForSharedAdaptiveMagazine() throws Exception {
        LevelDBChunk chunk = chunk(provider(), new ChunkSection[24], heights(), biomes());
        Runnable operation = () -> Data3dSerializer.serialize(new CapturingBatch(), chunk);
        operation.run(); withoutGlobalAdaptiveWait("data3d-write", operation);
    }

    @Test @Order(7)
    void data3dReadDoesNotWaitForSharedAdaptiveMagazine() throws Exception {
        LevelDBProvider provider = provider();
        CapturingBatch batch = new CapturingBatch();
        Data3dSerializer.serialize(batch, chunk(provider, new ChunkSection[24], heights(), biomes()));
        byte[] bytes = batch.value(LevelDBKey.DATA_3D.getKey(X, Z, 0));
        DB db = mock(DB.class); when(db.get(any(byte[].class))).thenReturn(bytes);
        Runnable operation = () -> Data3dSerializer.deserialize(db, new ChunkBuilder(X, Z, provider));
        operation.run(); withoutGlobalAdaptiveWait("data3d-read", operation);
    }

    @Test @Order(8)
    void pendingNbtDoesNotWaitForSharedAdaptiveMagazine() throws Exception {
        NbtMap ticks = ticks(); pending(ticks, true);
        // All mock construction is before the held magazine critical section.
        PendingFixture fixture = new PendingFixture(ticks, true);
        withoutGlobalAdaptiveWait("pending-nbt", () -> {
            try { fixture.append(new CapturingBatch()); }
            catch (Exception error) { throw new RuntimeException(error); }
        });
    }

    private static LevelDBProvider provider() {
        Level level = mock(Level.class);
        when(level.getDimensionData()).thenReturn(DIM); when(level.getDimension()).thenReturn(0);
        LevelDBProvider provider = mock(LevelDBProvider.class); when(provider.getLevel()).thenReturn(level);
        return provider;
    }
    private static int[] heights() {
        int[] result = new int[256]; result[1] = 319; result[2] = 32767; result[3] = 65535; return result;
    }
    private static PalettedBlockStorage[] biomes() {
        PalettedBlockStorage[] result = new PalettedBlockStorage[24];
        for (int i = 0; i < result.length; i++) result[i] = PalettedBlockStorage.createWithDefaultState(BitArrayVersion.V0, i < 2 ? 5 : 42);
        return result;
    }
    private static LevelDBChunk chunk(LevelDBProvider provider, ChunkSection[] sections, int[] heights, PalettedBlockStorage[] biomes) {
        return new LevelDBChunk(provider, X, Z, sections, heights, null, biomes, List.of(), List.of(), ChunkState.FINISHED);
    }
    private static ChunkBuilder decode(LevelDBProvider provider, byte[] bytes) {
        DB db = mock(DB.class); when(db.get(any(byte[].class))).thenReturn(bytes);
        ChunkBuilder builder = new ChunkBuilder(X, Z, provider); Data3dSerializer.deserialize(db, builder); return builder;
    }
    private static LevelDBChunkSection section(int y, int id) {
        StateBlockStorage[] layers = new StateBlockStorage[2];
        for (int layer = 0; layer < 2; layer++) {
            BitArray array = BitArrayVersion.V1.createPalette(4096); array.set(4095, 1);
            List<BlockStateSnapshot> palette = new ArrayList<>();
            for (int data = 0; data < 2; data++) palette.add(BlockStateSnapshot.builder().legacyId(id + layer).legacyData(data).runtimeId(100 + id * 4 + layer * 2 + data)
                    .vanillaState(NbtMap.builder().putString("name", "test:block_" + id + "_" + layer + "_" + data)
                            .putCompound("states", NbtMap.EMPTY).putInt("version", 1).build()).build());
            layers[layer] = new StateBlockStorage(array, palette, null, null);
        }
        return new LevelDBChunkSection(y, layers, true);
    }
    private static NbtMap ticks() {
        NbtMap entry = NbtMap.builder().putInt("x", -97).putInt("y", 319).putInt("z", 178)
                .putCompound("blockState", NbtMap.builder().putString("name", "test:block").putCompound("states", NbtMap.EMPTY).build())
                .putLong("time", 23).putInt("p", 7).build();
        return NbtMap.builder().putLong("currentTick", 0).putList("tickList", NbtType.COMPOUND, List.of(entry)).build();
    }
    private static CapturingBatch pending(NbtMap ticks, boolean nonempty) throws Exception {
        CapturingBatch batch = new CapturingBatch(); new PendingFixture(ticks, nonempty).append(batch); return batch;
    }
    private static final class PendingFixture {
        final LevelDBProvider provider; final LevelDBChunk chunk; final Method append;
        final DB database = mock(DB.class);
        PendingFixture(NbtMap ticks, boolean nonempty) throws Exception {
            provider = mock(LevelDBProvider.class, invocation -> invocation.getMethod().getName().equals("saveBlockTickingQueue") ? ticks : CALLS_REAL_METHODS.answer(invocation));
            Level level = mock(Level.class); when(level.getDimension()).thenReturn(0); when(level.getDimensionData()).thenReturn(DIM);
            field(provider.getClass(), "level").set(provider, level);
            chunk = mock(LevelDBChunk.class); when(chunk.getProvider()).thenReturn(provider);
            when(chunk.getState()).thenReturn(ChunkState.FINISHED); when(chunk.getX()).thenReturn(X); when(chunk.getZ()).thenReturn(Z);
            when(level.getPendingBlockUpdates(chunk)).thenReturn(nonempty ? Set.of(mock(BlockUpdateEntry.class)) : Set.of());
            field(provider.getClass(), "db").set(provider, database);
            append = LevelDBProvider.class.getDeclaredMethod("save0", int.class, int.class, LevelDBChunk.class); append.setAccessible(true);
        }
        void append(CapturingBatch batch) throws Exception { when(database.createWriteBatch()).thenReturn(batch); append.invoke(provider, X, Z, chunk); }
    }
    private static void withoutGlobalAdaptiveWait(String name, Runnable operation) throws Exception {
        Assumptions.assumeTrue(ByteBufAllocator.DEFAULT instanceof AdaptiveByteBufAllocator, "selected default must be Adaptive");
        Assumptions.assumeTrue(Runtime.getRuntime().maxMemory() > 512L * 1024 * 1024,
                "adaptive expansion is disabled when maximum heap is <=512 MiB");
        AdaptiveByteBufAllocator global = (AdaptiveByteBufAllocator) ByteBufAllocator.DEFAULT;
        AdaptiveByteBufAllocator fresh = new AdaptiveByteBufAllocator();
        Field directField = field(AdaptiveByteBufAllocator.class, "direct");
        Object savedDirect = directField.get(global), freshDirect = directField.get(fresh);
        Object[] sizeGroups = (Object[]) field(freshDirect.getClass(), "sizeClassedMagazineGroups").get(freshDirect);
        List<Object> groups = new ArrayList<>(Arrays.asList(sizeGroups));
        groups.add(field(freshDirect.getClass(), "largeBufferMagazineGroup").get(freshDirect));
        List<StampedLock> locks = new ArrayList<>(); List<Long> stamps = new ArrayList<>();
        AtomicReference<Thread> worker = new AtomicReference<>(); CountDownLatch started = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "p08-staging-" + name); worker.set(thread); return thread;
        });
        Future<?> future = null; boolean completed = false; boolean adaptiveStack = false;
        try {
            for (Object group : groups) {
                Object[] magazines = (Object[]) field(group.getClass(), "magazines").get(group);
                assertEquals(1, magazines.length, "fresh shared magazine");
                StampedLock lock = (StampedLock) field(magazines[0].getClass(), "allocationLock").get(magazines[0]);
                locks.add(lock); stamps.add(lock.writeLock());
            }
            directField.set(global, freshDirect);
            future = executor.submit(() -> { started.countDown(); operation.run(); });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            try { future.get(200, TimeUnit.MILLISECONDS); completed = true; }
            catch (TimeoutException expectedOnBaseline) {
                StackTraceElement[] stack = worker.get().getStackTrace();
                adaptiveStack = Arrays.stream(stack).anyMatch(frame -> frame.getClassName().equals("io.netty.buffer.AdaptivePoolingAllocator$Magazine") && frame.getMethodName().equals("free"));
                System.out.println("WAIT_STACK " + name + " " + Arrays.toString(stack));
            }
        } finally {
            for (int i = locks.size() - 1; i >= 0; i--) locks.get(i).unlockWrite(stamps.get(i));
            try { if (future != null) future.get(5, TimeUnit.SECONDS); }
            finally {
                directField.set(global, savedDirect); executor.shutdown();
                assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "worker cleanup deadline");
                for (Object group : groups) { Method free = group.getClass().getDeclaredMethod("free"); free.setAccessible(true); free.invoke(group); }
                assertEquals(0, fresh.usedDirectMemory(), "fresh pool direct memory released");
            }
        }
        System.out.println("STAGING_WAIT " + name + " completedBeforeUnlock=" + completed + " adaptiveRetirementStack=" + adaptiveStack);
        if (!completed) assertTrue(adaptiveStack, "timeout must be the exact native adaptive retirement path");
        assertTrue(completed, "native " + name + " byte[] staging must complete while unrelated shared adaptive magazine is held");
    }
    private static Field field(Class<?> type, String name) throws Exception {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try { Field result = current.getDeclaredField(name); result.setAccessible(true); return result; }
            catch (NoSuchFieldException missing) { }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }
    private static int unsignedShort(byte[] bytes, int index) { return (bytes[index] & 255) | (bytes[index + 1] & 255) << 8; }
    private static void corpus(String name, byte[] bytes) throws Exception {
        String expected = System.getProperty("p08.expectedCorpus");
        if (expected != null) assertArrayEquals(Files.readAllBytes(Path.of(expected, name + ".bin")), bytes, "baseline byte equality: " + name);
        String outputDirectory = System.getProperty("p08.corpus");
        if (outputDirectory != null) {
            Path output = Path.of(outputDirectory);
            Files.createDirectories(output);
            Files.write(output.resolve(name + ".bin"), bytes);
        }
        System.out.println("CORPUS " + name + " bytes=" + bytes.length + " sha256="
                + HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
    }
    private static final class CapturingBatch implements WriteBatch {
        final Map<String, byte[]> puts = new LinkedHashMap<>(); final List<byte[]> deletes = new ArrayList<>();
        public WriteBatch put(byte[] key, byte[] value) { puts.put(HexFormat.of().formatHex(key), value.clone()); return this; }
        public WriteBatch delete(byte[] key) { deletes.add(key.clone()); return this; }
        public int getApproximateSize() { return puts.values().stream().mapToInt(bytes -> bytes.length).sum(); }
        public int size() { return puts.size() + deletes.size(); }
        public void close() { }
        byte[] value(byte[] key) { byte[] bytes = valueOrNull(key); assertNotNull(bytes, "missing key " + HexFormat.of().formatHex(key)); return bytes; }
        byte[] valueOrNull(byte[] key) { return puts.get(HexFormat.of().formatHex(key)); }
        boolean deleted(byte[] key) { return deletes.stream().anyMatch(bytes -> Arrays.equals(key, bytes)); }
        byte[] deleteBytes() throws Exception { ByteArrayOutputStream bytes = new ByteArrayOutputStream(); for (byte[] key : deletes) bytes.write(key); return bytes.toByteArray(); }
        byte[] allBytes() throws Exception {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            for (var entry : puts.entrySet()) { bytes.write(HexFormat.of().parseHex(entry.getKey())); bytes.write(entry.getValue()); }
            bytes.write(deleteBytes()); return bytes.toByteArray();
        }
    }
}
