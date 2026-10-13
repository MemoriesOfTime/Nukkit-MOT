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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Timeout(30)
class PlayerDataLockLifecycleTest {
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
