package cn.nukkit.level.format.leveldb;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.level.DimensionEnum;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.ChunkReadTicket;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.level.generator.Flat;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBException;
import org.iq80.leveldb.WriteBatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.AdditionalAnswers;

import java.lang.reflect.Field;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real LevelDB regressions for the freshness fence retained through main-thread mount. */
class LevelDBChunkReadTicketTest {

    private static final int X = 9;
    private static final int Z = -4;
    private static final long HASH = Level.chunkHash(X, Z);

    @TempDir Path directory;
    private LevelDBProvider provider;
    private ExecutorService readers;
    private boolean previousAsync;
    private boolean previousCompletion;

    @BeforeEach
    void setUp() throws Exception {
        MockServer.init();
        Server server = Server.getInstance();
        this.previousAsync = server.asyncChunkSending;
        this.previousCompletion = server.asyncChunkLoadCompletion;
        server.asyncChunkSending = true;
        server.asyncChunkLoadCompletion = true;
        server.levelDbCache = 8;
        server.useNativeLevelDB = false;
        server.maxPendingChunkWrites = 128;
        LevelDBProvider.generate(this.directory.toString(), "read-ticket-test", 404L, Flat.class);
        Level level = mock(Level.class);
        when(level.getDimensionData()).thenReturn(DimensionEnum.OVERWORLD.getDimensionData());
        when(level.getDimension()).thenReturn(Level.DIMENSION_OVERWORLD);
        this.provider = new LevelDBProvider(level, this.directory.toString());
        this.readers = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void tearDown() {
        if (this.readers != null) this.readers.shutdownNow();
        if (this.provider != null) this.provider.close();
        Server.getInstance().asyncChunkSending = this.previousAsync;
        Server.getInstance().asyncChunkLoadCompletion = this.previousCompletion;
    }

    @Test
    void pendingWriteBarrierRunsOnWorkerAndReturnsLatestAcceptedSnapshot() throws Exception {
        LevelDBChunk chunk = snapshot(1);
        this.provider.saveChunkSync(X, Z, chunk);
        chunk.setBlock(0, 64, 0, 3);
        DB real = this.provider.getDatabase();
        DB delayed = mock(DB.class, AdditionalAnswers.delegatesTo(real));
        CountDownLatch writing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread main = Thread.currentThread();
        doAnswer(call -> {
            assertNotSame(main, Thread.currentThread());
            writing.countDown();
            assertTrue(release.await(10, TimeUnit.SECONDS));
            real.write(call.getArgument(0, WriteBatch.class));
            return null;
        }).when(delayed).write(any(WriteBatch.class));
        database(delayed);
        try {
            CompletableFuture<Void> ack = this.provider.saveChunkFuture(X, Z, chunk);
            assertTrue(writing.await(5, TimeUnit.SECONDS));
            try (ChunkReadTicket ticket = assertTimeout(Duration.ofMillis(500), () -> this.provider.openChunkRead(X, Z))) {
                CountDownLatch reading = new CountDownLatch(1);
                CompletableFuture<BaseFullChunk> read = CompletableFuture.supplyAsync(() -> {
                    reading.countDown();
                    return ticket.read();
                }, this.readers);
                assertTrue(reading.await(5, TimeUnit.SECONDS));
                assertFalse(read.isDone());
                assertFalse(ack.isDone(), "RAM staging cannot acknowledge a blocked write");
                assertThrows(IllegalStateException.class, () -> ticket.tryMount(this.provider.getEmptyChunk(X, Z)));
                assertNull(this.provider.getLoadedChunk(HASH));
                release.countDown();
                ack.get(10, TimeUnit.SECONDS);
                BaseFullChunk decoded = read.get(10, TimeUnit.SECONDS);
                assertEquals(3, decoded.getBlockId(0, 64, 0));
                assertSame(decoded, assertTimeout(Duration.ofMillis(500), () -> ticket.tryMount(decoded)));
            }
        } finally {
            release.countDown();
            drainWrites();
            database(real);
        }
    }

    @Test
    void newerStageAfterDecodeInvalidatesEvenAfterItsWriteSlotDisappears() throws Exception {
        LevelDBChunk original = snapshot(1);
        this.provider.saveChunkSync(X, Z, original);
        try (ChunkReadTicket stale = this.provider.openChunkRead(X, Z)) {
            BaseFullChunk old = read(stale);
            assertEquals(1, old.getBlockId(0, 64, 0));
            LevelDBChunk latest = snapshot(3);
            this.provider.saveChunkFuture(X, Z, latest).get(10, TimeUnit.SECONDS);
            drainWrites();
            assertEquals(0, this.provider.getPendingWriteCount(), "exercise the removed/recreated-slot ABA window");
            assertNull(stale.tryMount(old));
            assertNull(this.provider.getLoadedChunk(HASH));
        }
        try (ChunkReadTicket current = this.provider.openChunkRead(X, Z)) {
            BaseFullChunk decoded = read(current);
            assertEquals(3, decoded.getBlockId(0, 64, 0));
            assertSame(decoded, current.tryMount(decoded));
        }
    }

    @Test
    void twoDecodedCopiesProduceOnlyOneCanonicalMountedChunk() throws Exception {
        this.provider.saveChunkSync(X, Z, snapshot(1));
        try (ChunkReadTicket first = this.provider.openChunkRead(X, Z);
             ChunkReadTicket second = this.provider.openChunkRead(X, Z)) {
            BaseFullChunk one = read(first);
            BaseFullChunk two = read(second);
            assertNotSame(one, two);
            assertSame(one, first.tryMount(one));
            assertSame(one, second.tryMount(two));
            assertSame(one, this.provider.getLoadedChunk(HASH));
            assertEquals(1, this.provider.getLoadedChunks().size());
        }
    }

    @Test
    void mainThreadPublicationDoesNotWaitForTheLegacyProviderIoMonitor() throws Exception {
        this.provider.saveChunkSync(X, Z, snapshot(1));
        try (ChunkReadTicket ticket = this.provider.openChunkRead(X, Z)) {
            BaseFullChunk decoded = read(ticket);
            CountDownLatch holding = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            Thread legacyRead = new Thread(() -> {
                synchronized (this.provider) {
                    holding.countDown();
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                }
            }, "legacy-provider-monitor-test");
            legacyRead.start();
            try {
                assertTrue(holding.await(5, TimeUnit.SECONDS));
                assertSame(decoded, assertTimeout(Duration.ofMillis(500), () -> ticket.tryMount(decoded)));
            } finally {
                release.countDown();
                legacyRead.join(5_000);
            }
        }
    }

    @Test
    void failedReadIsNeverPermissionToMountAnEmptyChunk() throws Exception {
        DB real = this.provider.getDatabase();
        DB failed = mock(DB.class, AdditionalAnswers.delegatesTo(real));
        doThrow(new DBException("injected read failure")).when(failed).iterator();
        database(failed);
        try (ChunkReadTicket ticket = this.provider.openChunkRead(X, Z)) {
            ExecutionException exception = assertThrows(ExecutionException.class, () -> read(ticket));
            assertInstanceOf(DBException.class, exception.getCause());
            assertThrows(IllegalStateException.class, () -> ticket.tryMount(this.provider.getEmptyChunk(X, Z)));
            assertNull(this.provider.getLoadedChunk(HASH));
        } finally {
            database(real);
        }
    }

    @Test
    void malformedVersionHeaderIsFailureRatherThanAbsence() throws Exception {
        this.provider.getDatabase().put(LevelDBKey.VERSION.getKey(X, Z, 0), new byte[]{1, 2});
        try (ChunkReadTicket ticket = this.provider.openChunkRead(X, Z)) {
            assertThrows(ExecutionException.class, () -> read(ticket));
            assertThrows(IllegalStateException.class, () -> ticket.tryMount(this.provider.getEmptyChunk(X, Z)));
            assertNull(this.provider.getLoadedChunk(HASH));
        }
    }

    @Test
    void orphanActorDigestRetainsLegacyAbsenceWithoutChangingTheDigest() throws Exception {
        byte[] digestKey = LevelDBKey.getKey(LevelDBKey.DIGP_PREFIX, X, Z, 0);
        byte[] actorKey = new byte[]{0, 0, 0, 1, 0, 0, 0, 42};
        this.provider.getDatabase().put(digestKey, actorKey);
        try (ChunkReadTicket ticket = this.provider.openChunkRead(X, Z)) {
            assertNull(read(ticket));
            assertNull(this.provider.getLoadedChunk(HASH));
            assertArrayEquals(actorKey, this.provider.getDatabase().get(digestKey));
        }
    }

    @Test
    void ticketRejectsUnreadForeignAndReleasedCandidates() throws Exception {
        try (ChunkReadTicket ticket = this.provider.openChunkRead(X, Z)) {
            assertThrows(IllegalStateException.class, () -> ticket.tryMount(this.provider.getEmptyChunk(X, Z)));
            assertNull(read(ticket));
            assertThrows(IllegalArgumentException.class, () -> ticket.tryMount(this.provider.getEmptyChunk(X + 1, Z)));
            LevelDBChunk generated = snapshot(1);
            assertThrows(IllegalArgumentException.class, () -> ticket.tryMount(generated));
            ticket.close();
            ticket.close();
            assertNull(ticket.tryMount(this.provider.getEmptyChunk(X, Z)));
            assertThrows(CancellationException.class, ticket::read);
        }
    }

    @Test
    void twoDelayedUnloadReloadCyclesPreserveChestContentsAndActorUuid() throws Exception {
        CompoundTag item = new CompoundTag().putByte("Slot", 0).putShort("id", 264)
                .putShort("Damage", 0).putByte("Count", 7)
                .putCompound("tag", new CompoundTag().putString("CustomName", "saved-diamonds"));
        CompoundTag chest = new CompoundTag().putString("id", "Chest")
                .putInt("x", X << 4).putInt("y", 64).putInt("z", Z << 4)
                .putList(new ListTag<CompoundTag>("Items").add(item));
        String uuid = "ea4072f4-a56f-4b5e-85e2-78d4cb648723";
        CompoundTag actor = new CompoundTag().putString("id", "async_test_actor")
                .putLong("UniqueID", 987654321L).putString("uuid", uuid);
        byte[] actorBytes = NBTIO.write(actor, ByteOrder.LITTLE_ENDIAN);
        byte[] storageKey = new byte[]{0, 0, 0, 1, 0, 0, 0, 42};
        LevelDBChunk chunk = snapshot(54);
        chunk.setPreservedEntityActors(List.of(new LevelDBChunk.PreservedEntityActor(storageKey, actorBytes)));

        for (int cycle = 0; cycle < 2; cycle++) {
            attachChest(chunk, chest);
            chunk.setBlock(1, 64, 0, cycle == 0 ? 1 : 3);
            this.provider.setChunk(X, Z, chunk);
            chunk = unloadAndReloadWithBlockedDisk();
            @SuppressWarnings("unchecked")
            List<CompoundTag> tiles = (List<CompoundTag>) fieldValue(BaseFullChunk.class, chunk, "NBTtiles");
            assertEquals(List.of(chest), tiles, "container payload must survive once, without loss or duplication");
            assertEquals(1, chunk.getPreservedEntityActors().size());
            LevelDBChunk.PreservedEntityActor restored = chunk.getPreservedEntityActors().get(0);
            assertArrayEquals(storageKey, restored.getStorageKey());
            assertArrayEquals(actorBytes, restored.getRawNbt());
            assertEquals(uuid, NBTIO.read(restored.getRawNbt(), ByteOrder.LITTLE_ENDIAN).getString("uuid"));
            assertSame(chunk, this.provider.getLoadedChunk(HASH));
            assertEquals(1, this.provider.getLoadedChunks().size());
        }
    }

    @Test
    void versionlessColumnRetainsLegacyAbsenceAndItsBytes() throws Exception {
        this.provider.saveChunkSync(X, Z, snapshot(1));
        this.provider.getDatabase().delete(LevelDBKey.VERSION.getKey(X, Z, 0));
        this.provider.getDatabase().delete(LevelDBKey.VERSION_OLD.getKey(X, Z, 0));
        byte[] key = LevelDBKey.STATE_FINALIZATION.getKey(X, Z, 0);
        byte[] data = this.provider.getDatabase().get(key);
        assertNotNull(data);
        try (ChunkReadTicket ticket = this.provider.openChunkRead(X, Z)) {
            assertNull(read(ticket));
            assertArrayEquals(data, this.provider.getDatabase().get(key));
        }
        assertNull(this.provider.readChunk(X, Z));
    }

    @Test
    void legacyVersionStillLoadsWithCompletionEnabled() throws Exception {
        this.provider.saveChunkSync(X, Z, snapshot(1));
        byte[] version = this.provider.getDatabase().get(LevelDBKey.VERSION.getKey(X, Z, 0));
        this.provider.getDatabase().put(LevelDBKey.VERSION_OLD.getKey(X, Z, 0), version);
        this.provider.getDatabase().delete(LevelDBKey.VERSION.getKey(X, Z, 0));
        try (ChunkReadTicket ticket = this.provider.openChunkRead(X, Z)) {
            assertEquals(1, read(ticket).getBlockId(0, 64, 0));
        }
        assertEquals(1, this.provider.readChunk(X, Z).getBlockId(0, 64, 0));
    }

    private LevelDBChunk unloadAndReloadWithBlockedDisk() throws Exception {
        DB real = this.provider.getDatabase();
        DB delayed = mock(DB.class, AdditionalAnswers.delegatesTo(real));
        CountDownLatch writing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(call -> {
            writing.countDown();
            assertTrue(release.await(10, TimeUnit.SECONDS));
            real.write(call.getArgument(0, WriteBatch.class));
            return null;
        }).when(delayed).write(any(WriteBatch.class));
        database(delayed);
        try {
            assertTrue(assertTimeout(Duration.ofMillis(500), () -> this.provider.unloadChunk(X, Z, false)));
            assertTrue(writing.await(5, TimeUnit.SECONDS));
            assertNull(this.provider.getLoadedChunk(HASH));
            try (ChunkReadTicket ticket = this.provider.openChunkRead(X, Z)) {
                CompletableFuture<BaseFullChunk> read = CompletableFuture.supplyAsync(ticket::read, this.readers);
                assertFalse(read.isDone());
                release.countDown();
                BaseFullChunk decoded = read.get(10, TimeUnit.SECONDS);
                assertSame(decoded, ticket.tryMount(decoded));
                return (LevelDBChunk) decoded;
            }
        } finally {
            release.countDown();
            drainWrites();
            database(real);
        }
    }

    private void attachChest(LevelDBChunk chunk, CompoundTag tag) {
        BlockEntity tile = mock(BlockEntity.class);
        tile.namedTag = tag;
        when(tile.getId()).thenReturn(700L);
        when(tile.getFloorX()).thenReturn(X << 4);
        when(tile.getFloorY()).thenReturn(64);
        when(tile.getFloorZ()).thenReturn(Z << 4);
        when(tile.canSaveToStorage()).thenReturn(true);
        chunk.addBlockEntity(tile);
    }

    private LevelDBChunk snapshot(int blockId) {
        LevelDBChunk chunk = this.provider.getEmptyChunk(X, Z);
        chunk.setGenerated(true);
        chunk.setBlock(0, 64, 0, blockId);
        return chunk;
    }

    private BaseFullChunk read(ChunkReadTicket ticket) throws Exception {
        return CompletableFuture.supplyAsync(ticket::read, this.readers).get(10, TimeUnit.SECONDS);
    }

    private void drainWrites() throws Exception {
        ExecutorService writer = (ExecutorService) fieldValue(LevelDBProvider.class, this.provider, "executor");
        writer.submit(() -> { }).get(10, TimeUnit.SECONDS);
    }

    private void database(DB db) throws Exception {
        Field field = LevelDBProvider.class.getDeclaredField("db");
        field.setAccessible(true);
        field.set(this.provider, db);
    }

    private static Object fieldValue(Class<?> owner, Object target, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
