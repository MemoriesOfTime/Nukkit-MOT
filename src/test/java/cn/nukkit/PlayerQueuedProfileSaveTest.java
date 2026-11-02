package cn.nukkit;

import cn.nukkit.event.server.PlayerDataSerializeEvent;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.scheduler.*;
import cn.nukkit.utils.PlayerDataSerializer;
import cn.nukkit.utils.PlayerDataSaveQueue;
import cn.nukkit.utils.ManualPlayerDataQueue;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Timeout(20)
class PlayerQueuedProfileSaveTest {
    @Test void synchronousCheckpointFinishesOlderQueuedSaveBeforeItsOwnWrite() throws Exception {
        var f = new Fixture(true);
        f.server.saveOfflinePlayerData("player", new CompoundTag().putInt("value", 1), true);
        var newer = CompletableFuture.runAsync(() -> f.server.saveOfflinePlayerData("player", new CompoundTag().putInt("value", 2), false));
        assertTrue(f.writes.isEmpty()); f.runWrite(); f.runWrite(); newer.get(5, TimeUnit.SECONDS);
        assertEquals(List.of(1, 2), f.writes);
        assertTrue(f.queue.flushAll().toCompletableFuture().isDone());
        assertEquals(2, f.read().getInt("value"));
    }
    @Test void asyncSnapshotIncludesSerializeListenerChangesButDetachesBeforeReturning() throws Exception {
        var f = new Fixture(true);
        var live = new CompoundTag().putCompound("nested", new CompoundTag().putInt("value", 1));
        doAnswer(call -> { live.getCompound("nested").putInt("value", 7); return null; })
                .when(f.plugins).callEvent(any(PlayerDataSerializeEvent.class));
        f.server.saveOfflinePlayerData("player", live, true);
        live.getCompound("nested").putInt("value", 99);
        f.runWrite();
        assertEquals(7, f.read().getCompound("nested").getInt("value"));
        assertTrue(f.queue.flushAll().toCompletableFuture().isDone());
    }
    @Test void throwingSerializeListenerQueuesNothingAndHealthyRetryRunsOnlyOnce() throws Exception {
        var f = new Fixture(true); var failure = new AssertionError("serialization listener");
        doThrow(failure).when(f.plugins).callEvent(any(PlayerDataSerializeEvent.class));
        assertSame(failure, assertThrows(AssertionError.class, () ->
                f.server.saveOfflinePlayerData("player", new CompoundTag().putInt("value", 1), true)));
        assertTrue(f.queue.flushAll().toCompletableFuture().isDone()); assertTrue(f.scheduled.isEmpty()); assertTrue(f.writes.isEmpty());
        doNothing().when(f.plugins).callEvent(any(PlayerDataSerializeEvent.class));
        f.server.saveOfflinePlayerData("player", new CompoundTag().putInt("value", 2), true);
        f.runWrite(); assertTrue(f.scheduled.isEmpty());
        assertEquals(List.of(2), f.writes); assertTrue(f.queue.flushAll().toCompletableFuture().isDone());
    }

    static final class Fixture {
        final Server server = mock(Server.class, CALLS_REAL_METHODS);
        final PluginManager plugins = mock(PluginManager.class);
        final BlockingQueue<Runnable> scheduled = new LinkedBlockingQueue<>();
        final PlayerDataSaveQueue queue;
        final List<Integer> writes = new ArrayList<>();
        byte[] bytes;
        Fixture() throws Exception { this(false); }
        Fixture(boolean manual) throws Exception {
            queue = ManualPlayerDataQueue.create(manual ? scheduled::add : Runnable::run, Runnable::run, 16);
            inject("playerDataSaves", queue);
            inject("playerDataLocks", Server.createPlayerDataLocks());
            inject("pluginManager", plugins);
            inject("playerDataSerializer", new PlayerDataSerializer() {
                public Optional<InputStream> read(String name, UUID id) { return Optional.ofNullable(bytes).map(ByteArrayInputStream::new); }
                public OutputStream write(String name, UUID id) {
                    return new ByteArrayOutputStream() {
                        boolean closed;
                        public void close() throws IOException {
                            if (closed) return; closed = true;
                            super.close(); bytes = toByteArray(); writes.add(Fixture.this.read().getInt("value"));
                        }
                    };
                }
            });
            doReturn(true).when(server).shouldSavePlayerData(); server.savePlayerDataByUuid = false;
            doReturn(true).when(server).isPrimaryThread();
        }
        void runWrite() throws InterruptedException {
            Runnable task = scheduled.poll(5, TimeUnit.SECONDS); assertNotNull(task); task.run();
        }
        CompoundTag read() throws IOException { return NBTIO.readCompressed(new ByteArrayInputStream(bytes)); }
        void inject(String name, Object value) throws Exception {
            Field f = Server.class.getDeclaredField(name); f.setAccessible(true); f.set(server, value);
        }
    }
}
