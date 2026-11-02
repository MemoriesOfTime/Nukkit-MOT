package cn.nukkit.level.format.leveldb;

import cn.nukkit.GameVersion;
import cn.nukkit.MockServer;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.block.Block;
import cn.nukkit.Server;
import cn.nukkit.level.DimensionEnum;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.Chunk;
import cn.nukkit.level.format.leveldb.serializer.ChunkSerializerV3;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunkSection;
import cn.nukkit.level.format.leveldb.structure.StateBlockStorage;
import cn.nukkit.level.generator.Flat;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBException;
import org.iq80.leveldb.WriteBatch;
import org.iq80.leveldb.impl.WriteBatchImpl;
import org.iq80.leveldb.util.Slice;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.AdditionalAnswers;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Timeout(20)
class LevelDBSectionSaveAckTest {
    @TempDir Path directory;
    LevelDBProvider provider;
    DB raw;
    DB observed;
    final List<Integer> sectionBytes = new CopyOnWriteArrayList<>();

    @BeforeAll static void init() {
        MockServer.init();
        Server.getInstance().levelDbCache = 8;
        Server.getInstance().useNativeLevelDB = false;
    }

    @BeforeEach void setup() throws Exception {
        Server.getInstance().asyncChunkSending = true;
        LevelDBProvider.generate(directory.toString(), "section-ack", 404L, Flat.class);
        Level level = mock(Level.class);
        when(level.getDimensionData()).thenReturn(DimensionEnum.OVERWORLD.getDimensionData());
        when(level.getDimension()).thenReturn(Level.DIMENSION_OVERWORLD);
        provider = new LevelDBProvider(level, directory.toString());
        Field db = LevelDBProvider.class.getDeclaredField("db");
        db.setAccessible(true);
        raw = (DB) db.get(provider);
        observed = mock(DB.class, AdditionalAnswers.delegatesTo(raw));
        db.set(provider, observed);
        writes(batch -> { });
    }

    @AfterEach void close() {
        Server.getInstance().asyncChunkSending = false;
        if (provider != null) provider.close();
    }

    interface BeforeWrite { void run(WriteBatch batch) throws Exception; }

    void writes(BeforeWrite hook) {
        doAnswer(call -> {
            WriteBatch batch = call.getArgument(0);
            int[] bytes = {0};
            WriteBatch inspect = batch;
            if (!(inspect instanceof WriteBatchImpl)) {
                Field delegate = inspect.getClass().getDeclaredField("delegate");
                delegate.setAccessible(true);
                inspect = (WriteBatch) delegate.get(inspect);
            }
            ((WriteBatchImpl) inspect).forEach(new WriteBatchImpl.Handler() {
                public void put(Slice key, Slice value) {
                    byte[] k = key.getBytes();
                    // Overworld subchunk keys are x(4), z(4), tag(1), y(1).
                    if (k.length == 10 && k[8] == LevelDBKey.SUB_CHUNK_PREFIX.getKey(0, 0, 0, 0)[8]) bytes[0] += value.length();
                }
                public void delete(Slice key) { }
            });
            sectionBytes.add(bytes[0]);
            hook.run(batch);
            raw.write(batch);
            return null;
        }).when(observed).write(any(WriteBatch.class));
    }

    LevelDBChunk dirty(int x, int z) {
        LevelDBChunk chunk = provider.getEmptyChunk(x, z);
        chunk.setGenerated(true);
        provider.setChunk(x, z, chunk);
        chunk.setBlock(0, 64, 0, 1);
        return chunk;
    }

    LevelDBChunkSection section(LevelDBChunk chunk) { return (LevelDBChunkSection) chunk.getSection(4); }
    void save(LevelDBChunk chunk) throws Exception { provider.saveChunkFuture(chunk.getX(), chunk.getZ(), chunk).get(5, TimeUnit.SECONDS); }
    void await(CountDownLatch latch) throws Exception { assertTrue(latch.await(5, TimeUnit.SECONDS)); }

    @Test void acknowledgedSectionIsOmittedFromFollowingEntityOnlySaves() throws Exception {
        LevelDBChunk chunk = dirty(1, 2);
        save(chunk);
        assertFalse(section(chunk).isDirty());
        assertTrue(sectionBytes.get(0) > 0);
        for (int i = 0; i < 10; i++) {
            chunk.setChanged(); // Entity-only change: no block mutation.
            save(chunk);
        }
        assertEquals(11, sectionBytes.size());
        assertTrue(sectionBytes.subList(1, 11).stream().allMatch(bytes -> bytes == 0), sectionBytes.toString());
        assertEquals(1, provider.readChunk(1, 2).getBlockId(0, 64, 0));
    }

    @Test void firstAttachmentOfLoadedSectionsKeepsUnchangedSectionsClean() throws Exception {
        LevelDBChunk original = dirty(29, 30);
        original.setBlock(0, 80, 0, 3);
        save(original);
        LevelDBChunk loaded = provider.readChunk(29, 30);
        assertFalse(((LevelDBChunkSection) loaded.getSection(4)).isDirty());
        assertFalse(((LevelDBChunkSection) loaded.getSection(5)).isDirty());
        loaded.setBlock(0, 64, 0, 3);
        int[] puts = {0};
        writes(batch -> {
            WriteBatch inspect = batch;
            if (!(inspect instanceof WriteBatchImpl)) {
                Field delegate = inspect.getClass().getDeclaredField("delegate");
                delegate.setAccessible(true);
                inspect = (WriteBatch) delegate.get(inspect);
            }
            ((WriteBatchImpl) inspect).forEach(new WriteBatchImpl.Handler() {
                public void put(Slice key, Slice value) {
                    byte[] k = key.getBytes();
                    if (k.length == 10 && k[8] == LevelDBKey.SUB_CHUNK_PREFIX.getKey(0, 0, 0, 0)[8]) puts[0]++;
                }
                public void delete(Slice key) { }
            });
        });
        save(loaded);
        assertEquals(1, puts[0], "changing one loaded section must not rewrite its clean neighbours");
    }

    @Test void mutationAfterCaptureCannotBeClearedByOlderAck() throws Exception {
        LevelDBChunk chunk = dirty(3, 4);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        writes(batch -> { entered.countDown(); await(release); });
        try {
            CompletableFuture<Void> saved = provider.saveChunkFuture(3, 4, chunk);
            await(entered);
            chunk.setBlock(0, 64, 0, 3);
            release.countDown();
            saved.get(5, TimeUnit.SECONDS);
            assertTrue(section(chunk).isDirty());
            save(chunk);
            assertFalse(section(chunk).isDirty());
            assertEquals(3, provider.readChunk(3, 4).getBlockId(0, 64, 0));
        } finally { release.countDown(); }
    }

    @Test void failedWriteKeepsSectionDirtyForRetry() throws Exception {
        LevelDBChunk chunk = dirty(5, 6);
        writes(batch -> { throw new DBException("injected write failure"); });
        try {
            assertThrows(ExecutionException.class, () -> save(chunk));
            assertTrue(section(chunk).isDirty());
        } finally { writes(batch -> { }); }
        save(chunk);
        assertFalse(section(chunk).isDirty());
        assertEquals(1, provider.readChunk(5, 6).getBlockId(0, 64, 0));
    }

    @Test void supersededInflightAckDoesNotClearNewerSnapshotRevision() throws Exception {
        LevelDBChunk chunk = dirty(7, 8);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger writes = new AtomicInteger();
        writes(batch -> { if (writes.incrementAndGet() == 1) { entered.countDown(); await(release); } });
        try {
            CompletableFuture<Void> older = provider.saveChunkFuture(7, 8, chunk);
            await(entered);
            chunk.setBlock(0, 64, 0, 3);
            CompletableFuture<Void> newer = provider.saveChunkFuture(7, 8, chunk);
            release.countDown();
            older.get(5, TimeUnit.SECONDS);
            newer.get(5, TimeUnit.SECONDS);
            assertFalse(section(chunk).isDirty());
            assertEquals(3, provider.readChunk(7, 8).getBlockId(0, 64, 0));
            assertEquals(2, sectionBytes.size());
            assertTrue(sectionBytes.stream().allMatch(bytes -> bytes > 0));
        } finally { release.countDown(); }
    }

    @Test void reusingSectionInAnotherChunkInvalidatesOldOwnerAck() throws Exception {
        LevelDBChunk first = dirty(9, 10), second = dirty(11, 12);
        LevelDBChunkSection moved = section(first);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        writes(batch -> { entered.countDown(); await(release); });
        try {
            CompletableFuture<Void> older = provider.saveChunkFuture(9, 10, first);
            await(entered);
            second.setSection(4, moved);
            release.countDown();
            older.get(5, TimeUnit.SECONDS);
            assertTrue(moved.isDirty());
            save(second);
            assertFalse(moved.isDirty());
            assertEquals(1, provider.readChunk(11, 12).getBlockId(0, 64, 0));
        } finally { release.countDown(); }
    }

    @Test void replacingSectionDoesNotCleanItsReplacement() throws Exception {
        LevelDBChunk chunk = dirty(13, 14), other = dirty(15, 16);
        other.setBlock(0, 64, 0, 3);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        writes(batch -> { entered.countDown(); await(release); });
        try {
            CompletableFuture<Void> older = provider.saveChunkFuture(13, 14, chunk);
            await(entered);
            chunk.setSection(4, section(other));
            release.countDown();
            older.get(5, TimeUnit.SECONDS);
            assertTrue(section(chunk).isDirty());
            save(chunk);
            assertEquals(3, provider.readChunk(13, 14).getBlockId(0, 64, 0));
        } finally { release.countDown(); }
    }

    @Test void changedCoordinatesInvalidateCleanSectionsBeforeNextSave() throws Exception {
        LevelDBChunk chunk = dirty(17, 18);
        save(chunk);
        assertFalse(section(chunk).isDirty());
        chunk.setX(19); // Final base setter bypasses the section mutators.
        chunk.setZ(20);
        save(chunk);
        assertTrue(sectionBytes.get(1) > 0);
        assertEquals(1, provider.readChunk(19, 20).getBlockId(0, 64, 0));
    }

    @Test void directStorageMutationAndReplacementRemainVisibleAfterAck() throws Exception {
        LevelDBChunk chunk = dirty(21, 22);
        LevelDBChunkSection section = section(chunk);
        save(chunk);
        section.getStorages()[0].set(0, 0, 0, 3 << Block.DATA_BITS);
        save(chunk);
        assertEquals(3, provider.readChunk(21, 22).getBlockId(0, 64, 0));
        StateBlockStorage replacement = section.getStorages()[0].copy();
        replacement.set(0, 0, 0, 1 << Block.DATA_BITS);
        section.getStorages()[0] = replacement;
        save(chunk);
        assertEquals(1, provider.readChunk(21, 22).getBlockId(0, 64, 0));
        assertTrue(sectionBytes.stream().allMatch(bytes -> bytes > 0));
    }

    @Test void explicitDirtyRevisionCannotBeClearedEvenWhenStorageVersionIsUnchanged() throws Exception {
        LevelDBChunk chunk = dirty(25, 26);
        LevelDBChunkSection section = section(chunk);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        writes(batch -> { entered.countDown(); await(release); });
        try {
            CompletableFuture<Void> saved = provider.saveChunkFuture(25, 26, chunk);
            await(entered);
            section.setDirty();
            release.countDown();
            saved.get(5, TimeUnit.SECONDS);
            assertTrue(section.isDirty());
            save(chunk);
            assertFalse(section.isDirty());
        } finally { release.countDown(); }
    }

    @Test void legacySerializerAcceptsGenericChunkAndNeverAcknowledgesBeforeDbWrite() throws Exception {
        LevelDBChunk owner = dirty(27, 28);
        Chunk generic = mock(Chunk.class);
        when(generic.getProvider()).thenReturn(provider);
        when(generic.getX()).thenReturn(27);
        when(generic.getZ()).thenReturn(28);
        when(generic.getSection(4)).thenReturn(section(owner));
        try (WriteBatch batch = raw.createWriteBatch()) {
            assertDoesNotThrow(() -> ChunkSerializerV3.INSTANCE.serializer(batch, generic));
        }
        assertTrue(section(owner).isDirty(), "legacy serializer has no successful-write ACK");
    }

    @Test void dirtyAckDoesNotChangeNetworkBytesAcrossSupportedClientWindow() throws Exception {
        LevelDBChunk chunk = dirty(31, 32);
        LevelDBChunkSection section = section(chunk);
        Map<GameVersion, byte[]> before = new LinkedHashMap<>();
        for (GameVersion version : GameVersion.values()) {
            if (!version.isNetEase() && version.getProtocol() >= GameVersion.V1_20_0.getProtocol()
                    && version.getProtocol() <= GameVersion.V1_26_50.getProtocol()) {
                BinaryStream stream = new BinaryStream();
                section.writeTo(version, stream, false);
                before.put(version, stream.getBuffer());
            }
        }
        assertTrue(before.size() >= 20);
        save(chunk);
        assertFalse(section.isDirty());
        for (Map.Entry<GameVersion, byte[]> entry : before.entrySet()) {
            BinaryStream stream = new BinaryStream();
            section.writeTo(entry.getKey(), stream, false);
            assertArrayEquals(entry.getValue(), stream.getBuffer(), entry.getKey().toString());
        }
    }

    @Test void ackNeverWaitsForSectionWriteLock() throws Exception {
        LevelDBChunk chunk = dirty(23, 24);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        writes(batch -> { entered.countDown(); await(release); });
        Field writeLock = LevelDBChunkSection.class.getDeclaredField("writeLock");
        writeLock.setAccessible(true);
        Lock heldByMain = (Lock) writeLock.get(section(chunk));
        try {
            CompletableFuture<Void> saved = provider.saveChunkFuture(23, 24, chunk);
            await(entered);
            heldByMain.lock();
            try {
                release.countDown();
                saved.get(5, TimeUnit.SECONDS);
                assertTrue(section(chunk).isDirty(), "skipped ACK must preserve the next save");
            } finally { heldByMain.unlock(); }
            save(chunk);
            assertFalse(section(chunk).isDirty());
        } finally { release.countDown(); }
    }
}
