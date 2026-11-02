package cn.nukkit;

import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.scheduler.ServerScheduler;
import cn.nukkit.scheduler.Task;
import cn.nukkit.scheduler.TaskHandler;
import cn.nukkit.utils.PlayerDataMigrator;
import cn.nukkit.utils.PlayerDataSerializer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.*;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerDataLockTest {

    @Nested
    @Timeout(60)
    class Retention {
        @Test void completedIdentitiesDoNotRemainStronglyReachable() throws Exception {
            var locks = Server.createPlayerDataLocks();
            List<WeakReference<ReentrantLock>> observed = new ArrayList<>();
            for (int i = 0; i < 20_000; i++) {
                observed.add(new WeakReference<>(locks.computeIfAbsent("identity-" + i, k -> new ReentrantLock())));
            }
            for (int attempt = 0; attempt < 100 && observed.stream().anyMatch(r -> r.get() != null); attempt++) {
                System.gc();
                Thread.sleep(20);
            }
            assertTrue(observed.stream().allMatch(r -> r.get() == null), "completed lock objects remain rooted");
            // MapMaker drains cleared references on subsequent reads/writes, not on an idle timer.
            for (int i = 0; i < 100_000 && !locks.isEmpty(); i++) locks.get("maintenance-" + i);
            assertTrue(locks.isEmpty(), "cleared value entries keep identity strings");
        }

        @Test void heldWaitedAndReentrantLocksAreNotReplacedByCollection() throws Exception {
            var locks = Server.createPlayerDataLocks();
            ReentrantLock held = locks.computeIfAbsent("identity", k -> new ReentrantLock());
            held.lock();
            ExecutorService worker = Executors.newSingleThreadExecutor();
            try {
                CountDownLatch lookedUp = new CountDownLatch(1);
                Future<?> waiter = worker.submit(() -> {
                    ReentrantLock lock = locks.computeIfAbsent(new String("identity"), k -> new ReentrantLock());
                    assertSame(held, lock);
                    lookedUp.countDown();
                    lock.lock();
                    try { assertSame(lock, locks.computeIfAbsent("identity", k -> new ReentrantLock())); }
                    finally { lock.unlock(); }
                });
                assertTrue(lookedUp.await(2, TimeUnit.SECONDS));
                for (int i = 0; i < 5; i++) System.gc();
                ReentrantLock nested = locks.computeIfAbsent(new String("identity"), k -> new ReentrantLock());
                assertSame(held, nested);
                nested.lock();
                assertEquals(2, held.getHoldCount());
                nested.unlock();
                assertFalse(waiter.isDone());
                held.unlock();
                waiter.get(5, TimeUnit.SECONDS);
                Reference.reachabilityFence(held);
            } finally {
                if (held.isHeldByCurrentThread()) held.unlock();
                worker.shutdownNow();
            }
        }

        @Test void concurrentLookupAndCollectionNeverSplitTheCriticalSection() throws Exception {
            var locks = Server.createPlayerDataLocks();
            AtomicInteger inSection = new AtomicInteger(), overlaps = new AtomicInteger(), completed = new AtomicInteger();
            ExecutorService workers = Executors.newFixedThreadPool(8);
            List<Future<?>> futures = new ArrayList<>();
            try {
                for (int w = 0; w < 8; w++) {
                    int worker = w;
                    futures.add(workers.submit(() -> {
                        for (int n = 0; n < 10_000; n++) {
                            ReentrantLock lock = locks.computeIfAbsent(new String("contended"), k -> new ReentrantLock());
                            lock.lock();
                            try {
                                if (inSection.incrementAndGet() != 1) overlaps.incrementAndGet();
                                completed.incrementAndGet();
                                inSection.decrementAndGet();
                            } finally { lock.unlock(); }
                            // System.gc() is stop-the-world: one caller clears weak values for all workers;
                            // letting every worker call it just multiplies full-GC cost on slow CI machines.
                            if (worker == 0 && n % 2_500 == 0) System.gc();
                        }
                    }));
                }
                for (Future<?> future : futures) future.get(60, TimeUnit.SECONDS);
                assertEquals(80_000, completed.get());
                assertEquals(0, overlaps.get());
            } finally { workers.shutdownNow(); }
        }
    }

    @Nested
    @Timeout(30)
    class Lifecycle {
        private static final UUID PREVIOUS = UUID.fromString("6a1b7ac6-2f0f-4a6a-9f43-2b6c4a1d0e11");
        private static final UUID CURRENT = UUID.fromString("762705ea-dcc6-4dfe-b281-20b8d1ffa86b");

        @Test void collectedRegistrationLockStillFlushesQueuedSaveBeforeMigration() throws Exception {
            Fixture f = new Fixture();
            f.server.saveOfflinePlayerData(PREVIOUS, new CompoundTag().putInt("value", 42), true);
            for (int i = 0; i < 5; i++) System.gc();
            assertEquals(PlayerDataMigrator.Result.MIGRATED, f.server.migratePlayerData(PREVIOUS, CURRENT));
            assertEquals(42, f.read(CURRENT));
            assertEquals(1, f.serializer.previousWrites.get());
            assertTrue(f.pending.isEmpty());
            // A worker dispatched before the synchronous flush must not write the old path again.
            f.scheduled.get(0).onRun(1);
            assertEquals(1, f.serializer.previousWrites.get());
        }

        @Test void runningAsyncSaveSharesLockWithReaderAndMigrationAcrossCollection() throws Exception {
            Fixture f = new Fixture();
            f.serializer.block = true;
            f.server.saveOfflinePlayerData(PREVIOUS, new CompoundTag().putInt("value", 73), true);
            ExecutorService workers = Executors.newFixedThreadPool(3);
            try {
                Future<?> save = workers.submit(() -> f.scheduled.get(0).onRun(1));
                assertTrue(f.serializer.writeStarted.await(3, TimeUnit.SECONDS));
                CountDownLatch callersReady = new CountDownLatch(2);
                Future<CompoundTag> read = workers.submit(() -> {
                    callersReady.countDown();
                    return f.server.getOfflinePlayerData(PREVIOUS, false);
                });
                Future<PlayerDataMigrator.Result> migration = workers.submit(() -> {
                    callersReady.countDown();
                    return f.server.migratePlayerData(PREVIOUS, CURRENT);
                });
                assertTrue(callersReady.await(3, TimeUnit.SECONDS));
                for (int i = 0; i < 5; i++) System.gc();
                assertFalse(read.isDone(), "read escaped an active write lock");
                assertFalse(migration.isDone(), "migration escaped an active write lock");
                f.serializer.releaseWrite.countDown();
                save.get(5, TimeUnit.SECONDS);
                assertEquals(73, read.get(5, TimeUnit.SECONDS).getInt("value"));
                assertEquals(PlayerDataMigrator.Result.MIGRATED, migration.get(5, TimeUnit.SECONDS));
                assertEquals(73, f.read(CURRENT));
                assertEquals(1, f.serializer.previousWrites.get());
                assertTrue(f.pending.isEmpty());
            } finally {
                f.serializer.releaseWrite.countDown();
                workers.shutdownNow();
            }
        }

        @Test void reverseConcurrentMigrationsKeepGlobalLockOrder() throws Exception {
            Fixture f = new Fixture();
            ExecutorService workers = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            try {
                Future<?> forward = workers.submit(() -> {
                    start.await();
                    for (int i = 0; i < 100; i++) assertEquals(PlayerDataMigrator.Result.SKIPPED,
                            f.server.migratePlayerData(PREVIOUS, CURRENT));
                    return null;
                });
                Future<?> backward = workers.submit(() -> {
                    start.await();
                    for (int i = 0; i < 100; i++) assertEquals(PlayerDataMigrator.Result.SKIPPED,
                            f.server.migratePlayerData(CURRENT, PREVIOUS));
                    return null;
                });
                start.countDown();
                forward.get(10, TimeUnit.SECONDS);
                backward.get(10, TimeUnit.SECONDS);
            } finally { workers.shutdownNow(); }
        }

        private static final class Fixture {
            final Server server = mock(Server.class, CALLS_REAL_METHODS);
            final MemorySerializer serializer = new MemorySerializer();
            final ConcurrentHashMap<String, ArrayDeque<Task>> pending = new ConcurrentHashMap<>();
            final List<Task> scheduled = new CopyOnWriteArrayList<>();
            Fixture() throws Exception {
                inject(server, "playerDataLocks", Server.createPlayerDataLocks());
                inject(server, "pendingPlayerDataSaves", pending);
                inject(server, "playerDataSerializer", serializer);
                inject(server, "pluginManager", mock(PluginManager.class));
                ServerScheduler scheduler = mock(ServerScheduler.class);
                doReturn(scheduler).when(server).getScheduler();
                doReturn(true).when(server).shouldSavePlayerData();
                server.savePlayerDataByUuid = false;
                when(scheduler.scheduleTask(any(Plugin.class), any(Runnable.class), eq(true))).thenAnswer(call -> {
                    Task task = call.getArgument(1);
                    TaskHandler handler = new TaskHandler(call.getArgument(0), task, scheduled.size() + 1, true);
                    task.setHandler(handler);
                    scheduled.add(task);
                    return handler;
                });
            }
            int read(UUID id) throws IOException {
                try (InputStream input = serializer.read(id.toString(), id).orElseThrow()) {
                    return NBTIO.readCompressed(input).getInt("value");
                }
            }
        }

        private static final class MemorySerializer implements PlayerDataSerializer {
            final Map<String, byte[]> data = new ConcurrentHashMap<>();
            final CountDownLatch writeStarted = new CountDownLatch(1), releaseWrite = new CountDownLatch(1);
            final AtomicInteger previousWrites = new AtomicInteger();
            volatile boolean block;
            public Optional<InputStream> read(String name, UUID id) {
                return Optional.ofNullable(data.get(name)).map(ByteArrayInputStream::new);
            }
            public OutputStream write(String name, UUID id) throws IOException {
                if (name.equals(PREVIOUS.toString())) {
                    previousWrites.incrementAndGet();
                    if (block) {
                        writeStarted.countDown();
                        try {
                            if (!releaseWrite.await(10, TimeUnit.SECONDS)) throw new IOException("test write timeout");
                        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
                    }
                }
                return new ByteArrayOutputStream() {
                    public void close() throws IOException { super.close(); data.put(name, toByteArray()); }
                };
            }
            public boolean delete(String name, UUID id) { return data.remove(name) != null; }
        }

        private static void inject(Server server, String name, Object value) throws Exception {
            Field field = Server.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(server, value);
        }
    }
}
